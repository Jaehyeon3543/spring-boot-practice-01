package com.example.ksl.wsd2025.api.v4;

import com.example.ksl.wsd2025.api.dto.ItemDto;
import com.example.ksl.wsd2025.api.request.ItemCreateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v4/items")
public class ItemController4 {

    // 메모리용 임시 DB (예제용)
    private final Map<Long, ItemDto> store = new HashMap<>();
    private long sequence = 1L;

    // 1-1. POST: 여러 개 한꺼번에 생성
    // 요청 바디: [ {"name":"a","price":1000}, {"name":"b","price":2000} ] 여러 개 한꺼번에 생성
    @PostMapping("/bulk")
    public ResponseEntity<Map<String, Object>> createItems(@RequestBody List<ItemCreateRequest> requests) {
        // 빈 배열이면 400 Bad Request
        if (requests.isEmpty()) {
            return error(400, "EMPTY_ITEM_LIST", "생성할 아이템이 없습니다.", null);
        }

        // 하나라도 값이 잘못되면 아무것도 만들지 않고 400 Bad Request
        for (int i = 0; i < requests.size(); i++) {
            ItemCreateRequest request = requests.get(i);
            if (request.getName() == null || request.getName().isBlank()) {
                return error(400, "INVALID_ITEM_NAME", "아이템 이름(name)은 비어 있을 수 없습니다.", (i + 1) + "번째 아이템");
            }
            if (request.getPrice() == null || request.getPrice() < 0) {
                return error(400, "INVALID_ITEM_PRICE", "아이템 가격(price)은 0 이상이어야 합니다.",
                        (i + 1) + "번째 아이템, 요청된 price: " + request.getPrice());
            }
        }

        List<ItemDto> createdItems = new ArrayList<>();

        // 요청 바디에 있는 여러 개 아이템을 하나씩 생성
        for (ItemCreateRequest request : requests) {    
            ItemDto item = new ItemDto();
            item.setId(sequence++);
            item.setName(request.getName());
            item.setPrice(request.getPrice());

            store.put(item.getId(), item);
            createdItems.add(item);
        }
        return created(createdItems);   // 201 Created
    }

    // 1-2. POST: 기존 아이템을 복제해서 새 아이템 생성
    @PostMapping("/{id}/copy")
    public ResponseEntity<Map<String, Object>> copyItem(@PathVariable Long id) {
        ItemDto origin = store.get(id);

         // 원본 아이템이 존재하지 않으면 404 Not Found 반환
        if (origin == null) {  
            return error(404, "ITEM_NOT_FOUND", "해당 아이템을 찾을 수 없습니다.", "요청된 ID: " + id);
        }

        ItemDto copy = new ItemDto();   //
        copy.setId(sequence++);
        copy.setName(origin.getName() + " (copy)");     // 복제된 아이템 이름에 "(copy)" 추가
        copy.setPrice(origin.getPrice());

        store.put(copy.getId(), copy);

        return created(copy);   // 201 Created
    }

    // GET: 전체 조회 (기존 v1~v3 에서 구현한 것과 동일)
     @GetMapping
    public List<ItemDto> getItems() {
        return new ArrayList<>(store.values());
    }

    // 2-1. GET: 아이템 개수와 가격 합산 조회
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        int count = store.size();

        // 가격 합산 계산
        // Math.addExact: 합계가 int 범위(약 21억)를 넘으면 ArithmeticException 발생
        // (그냥 + 로 더하면 오류 없이 음수 같은 엉뚱한 값이 나옴)
        int totalPrice = 0;
        try {
            for (ItemDto item : store.values()) {
                totalPrice = Math.addExact(totalPrice, item.getPrice());
            }
        } catch (ArithmeticException e) {
            // 서버가 계산할 수 없는 상황 → 500 Internal Server Error
            return error(500, "PRICE_SUM_OVERFLOW", "가격 합계가 계산 가능한 범위를 초과했습니다.", "아이템 수: " + count);
        }

        return success(Map.of("count", count, "totalPrice", totalPrice));
    }

    // 2-2. GET: 가격 범위로 조회 (가격 낮은 순 정렬)
    // ex) /api/v4/items/price-range?min=1000&max=5000
    @GetMapping("/price-range")
    public ResponseEntity<Map<String, Object>> getItemsByPriceRange(
            @RequestParam(defaultValue = "0") int min,
            @RequestParam(defaultValue = "" + Integer.MAX_VALUE) int max    // 기본값 설정: min=0, max=Integer.MAX_VALUE
    ) {
        // 가격 범위에 맞는 아이템 필터링 후 가격 낮은 순으로 정렬
        List<ItemDto> items = store.values().stream()
                .filter(item -> item.getPrice() >= min && item.getPrice() <= max)
                .sorted(Comparator.comparing(ItemDto::getPrice))
                .toList();

        return success(items);
    }

    // 3-1. PUT: 특정 아이템 할인 적용
    // ex) /api/v4/items/1/discount?rate=10  → 10% 할인
    @PutMapping("/{id}/discount")
    public ResponseEntity<Map<String, Object>> discountItem(
            @PathVariable Long id,
            @RequestParam int rate
    ) {
        // 할인율이 0~100 범위를 벗어나면 400 Bad Request
        if (rate < 0 || rate > 100) {
            return error(400, "INVALID_DISCOUNT_RATE", "할인율(rate)은 0~100 사이여야 합니다.", "요청된 rate: " + rate);
        }

        ItemDto item = store.get(id);
        if (item == null) {
            return error(404, "ITEM_NOT_FOUND", "해당 아이템을 찾을 수 없습니다.", "요청된 ID: " + id);
        }

        item.setPrice(applyDiscount(item.getPrice(), rate));

        return success(item);
    }

    // 3-2. PUT: 전체 아이템 일괄 할인 적용
    // ex)  /api/v4/items/discount?rate=10
    @PutMapping("/discount")
    public ResponseEntity<Map<String, Object>> discountAllItems(@RequestParam int rate) {
        // 할인율이 0~100 범위를 벗어나면 400 Bad Request
        if (rate < 0 || rate > 100) {
            return error(400, "INVALID_DISCOUNT_RATE", "할인율(rate)은 0~100 사이여야 합니다.", "요청된 rate: " + rate);
        }

        // 모든 아이템에 대해 할인 적용
        for (ItemDto item : store.values()) {
            item.setPrice(applyDiscount(item.getPrice(), rate));
        }
        return success(new ArrayList<>(store.values()));
    }

    // 4-1. DELETE: 전체 삭제 (삭제된 개수 반환)
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteAllItems() {
        int count = store.size();
        store.clear();
        // 삭제된 개수 반환
        return success(Map.of("deletedCount", count));
    }

    // 4-2. DELETE: 특정 가격 이하 아이템 삭제
    // ex) /api/v4/items/by-price?max=1000
    @DeleteMapping("/by-price")
    public ResponseEntity<Map<String, Object>> deleteItemsByPrice(@RequestParam int max) {
        int before = store.size();
        // 가격이 max 이하인 아이템을 제거
        store.values().removeIf(item -> item.getPrice() <= max);
        int deletedCount = before - store.size();
        return success(Map.of("deletedCount", deletedCount));
    }

    // 할인가 계산 (price * (100 - rate) 가 int 범위를 넘지 않도록 long 으로 계산)
    private int applyDiscount(int price, int rate) {
        return (int) ((long) price * (100 - rate) / 100);
    }

    //---------------------------------------------------------------------------------------------
    // 예외 처리: Spring 이 자동으로 내던 400 / 500 을 같은 포맷으로 응답
    // (컨트롤러 안의 @ExceptionHandler 는 이 컨트롤러에서 난 예외만 처리 → v1~v3 에는 영향 없음)

    // 400: 필수 파라미터 누락 (ex. /discount 에 rate 없음)
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException e) {
        return error(400, "MISSING_PARAMETER", "필수 파라미터가 없습니다.", "누락된 파라미터: " + e.getParameterName());
    }

    // 400: 파라미터 타입 불일치 (ex. rate=abc, /items/abc/copy)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return error(400, "INVALID_PARAMETER", "파라미터 형식이 올바르지 않습니다.", e.getName() + "=" + e.getValue());
    }

    // 400: 요청 바디(JSON) 를 읽을 수 없음 (바디 없음, JSON 문법 오류, price 에 문자 등)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException e) {
        return error(400, "INVALID_REQUEST_BODY", "요청 바디(JSON) 형식이 올바르지 않습니다.", null);
    }

    // 500: 위에서 처리하지 못한 모든 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        System.out.println("[ItemController4] 서버 내부 오류: " + e);
        return error(500, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.", null);
    }

    //---------------------------------------------------------------------------------------------
    // 응답 포맷 통일용 메소드
    // 성공: { "success": true,  "data": ... }
    // 실패: { "success": false, "error": { "code": "ITEM_NOT_FOUND", "message": "...", "detail": "..." } }

    // 200 OK
    private ResponseEntity<Map<String, Object>> success(Object data) {
        return ResponseEntity.status(200).body(successBody(data));
    }

    // 201 Created
    private ResponseEntity<Map<String, Object>> created(Object data) {
        return ResponseEntity.status(201).body(successBody(data));
    }

    private Map<String, Object> successBody(Object data) {
        Map<String, Object> body = new LinkedHashMap<>();   // LinkedHashMap: 넣은 순서대로 JSON 출력
        body.put("success", true);
        body.put("data", data);
        return body;
    }

    // 4xx / 5xx
    // status: HTTP 상태 코드 / code: 에러 종류 / message: 에러 설명 / detail: 추가 정보 (없으면 null)
    private ResponseEntity<Map<String, Object>> error(int status, String code, String message, String detail) {
        System.out.println("[ItemController4] 요청 실패: status=" + status + ", code=" + code);

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (detail != null) {
            error.put("detail", detail);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", error);
        return ResponseEntity.status(status).body(body);
    }
}
