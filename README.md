# 실습 1

기존에 제공해주신 예제 프로젝트에 "v4" 파일을 생성해서 실습을 진행했습니다.

* 컨트롤러 : `springboot-tutorial/src/main/java/com/example/ksl/wsd2025/api/v4/ItemController4.java`
* 미들웨어 : `springboot-tutorial/src/main/java/com/example/ksl/wsd2025/config/LoggingInterceptor.java`
* Postman 테스트 : `springboot-tutorial/postman/ItemController4.postman_collection.json`
* 기본 주소 : `http://localhost:8080/api/v4/items`

## A. HTTP 메소드별 API 구현

* POST / GET / PUT / DELETE 각각 2개씩, 총 8개의 API 구성

기존 프로젝트에 존재하던 API의 기능과는 조금씩 다르게 구성했습니다.

1. POST 
    1-1. 여러 개의 아이템을 한꺼번에 생성합니다.
        * Annotation : @PostMapping("/bulk")
        * Data Receiving : Body
        * explanation
            - 여러 개의 데이터를 한꺼번에 생성해야하니 List로 데이터를 받고 반환했습니다.

    1-2. 기존의 아이템을 복사합니다.
        * Annotation : @PostMapping("/{id}/copy")
        * Data Receiving : Pathvariable
        * explanation
            - 기존에 존재하던 아이템의 복사본을 생성합니다. 기존 아이템은 Pathvariable 정보를 통해 확인합니다.
              복사본임을 알려주기 위해 복사본 아이템은 이름 뒤에 "(copy)" 를 붙여줍니다.
2. GET
    2-1. 아이템들의 개수와 가격 합계를 가져옵니다.
        * Annotation : @GetMapping("/summary")
        * Data Receiving : X
        * explanation
            - 메모리용 임시 DB에서 아이템들의 개수와 가격의 합입니다.


    2-2. 가격의 범위로 조회합니다.
        *Annotation : @GetMapping("/price-range")
        *Data Receiving : Parameter
        * explanation
            - Parameter로 가격의 범위를 받고, stream으로 필터링 및 정렬을 했습니다.


3. PUT
    3-1. 아이템 1개의 가격을 할인합니다.
        * Annotation : @PutMapping("/{id}/discount")
        * Data Receiving : Pathvariable, Parameter
        * explanation
            - 특정 아이템은 Pathvariable 데이터를 받아 특정하고, 그 아이템의 가격을 수정했습니다.


    3-2. 모든 아이템을 할인합니다.
        * Annotation : @PutMapping("/discount")
        * Data Receiving : Parameter
        * explanation
            - 모든 아이템의 가격을 수정했습니다. 여러 개의 데이터이다 보니 List로 했습니다.


4. DELETE
    4-1. 모든 아이템을 삭제합니다.
        * Annotation : @DeleteMapping
        * Data Receiving : X
        * explanation
            - 모든 아이템을 삭제했습니다. 또한 삭제한 개수를 응답으로 알려줬습니다.


    4-2. 지정한 가격 이하의 아이템만 삭제합니다
        * Annotation : @DeleteMapping("/by-price")
        * Data Receiving : Parameter
        * explanation
            - removeIf를 사용해서 조건에 맞는 아이템들만 삭제했습니다. 이후에 삭제한 아이템의 개수를 응답했습니다.


## B. 미들웨어 구현

* 기존 프로젝트의 LoggingInterceptor 에 v4 전용 기능 2개를 추가했습니다.

기존에 있던 요청 로그 기능은 그대로 두고, 로그와 다른 기능으로 구성했습니다.
두 기능 모두 컨트롤러가 실행되기 전(preHandle)에 동작하고, v4 API(/api/v4) 에만 적용되도록 해서 v1 ~ v3 에는 영향이 없습니다.

1. 점검 모드
    * Location : LoggingInterceptor.preHandle()
    * Response Code : 503 Service Unavailable
    * explanation
        - 점검 모드가 켜져 있으면 모든 v4 요청을 컨트롤러로 보내지 않고 바로 503 으로 응답합니다.
        - 점검 모드는 @Value("${app.maintenance:false}") 로 실행할 때 설정값을 받아서 켜고 끕니다. (기본값 false)
        - 켜는 법 : ./gradlew bootRun --args='--app.maintenance=true'

2. API 키 인증
    * Location : LoggingInterceptor.preHandle()
    * Response Code : 401 Unauthorized
    * explanation
        - 데이터를 바꾸는 요청(POST / PUT / DELETE)은 헤더에 X-API-KEY: wsd2025 가 있어야 통과합니다.
        - 조회(GET)는 API 키 없이 가능합니다.
        - 점검 모드를 먼저 검사하기 때문에, 점검 중에는 API 키가 맞아도 503 으로 차단됩니다.

* 요청 처리 순서

```
요청 → 요청 로그 출력 → (v4 가 아니면 통과) → 점검 모드면 503 → API 키가 틀리면 401 → 컨트롤러
```

* 거절할 때는 preHandle 에서 false 를 반환해서 컨트롤러로 넘어가지 않도록 했습니다. 거절 이유는 서버 로그로도 출력됩니다.

```
[LoggingInterceptor] 요청 시작: DELETE /api/v4/items
[LoggingInterceptor] 요청 거절: status=401, code=INVALID_API_KEY
```


## C. 응답 코드 다양성 확보

* 2xx, 4xx, 5xx 각 범주에서 2개 이상의 응답 코드를 사용했습니다.

| 범주 | 응답 코드 | 상황 |
|---|---|---|
| 2xx | 200 OK | 조회 / 수정 / 삭제 성공 |
| 2xx | 201 Created | 생성 성공 (1-1 여러 개 생성, 1-2 복제) |
| 4xx | 400 Bad Request | 요청 값이 잘못됨 (빈 배열, name 빈칸, price 음수, 할인율 0~100 밖, 파라미터 누락, 형식 오류) |
| 4xx | 401 Unauthorized | POST / PUT / DELETE 에 API 키가 없거나 틀림 (미들웨어) |
| 4xx | 404 Not Found | 복제 / 할인하려는 아이템이 없음 |
| 5xx | 500 Internal Server Error | 가격 합계가 int 범위(약 21억)를 넘음, 그 외 예상하지 못한 예외 |
| 5xx | 503 Service Unavailable | 점검 모드 (미들웨어) |

1. 400 Bad Request
    * explanation
        - 요청 값은 컨트롤러에서 직접 검사했습니다. bulk 생성은 하나라도 값이 잘못되면 아무것도 생성하지 않습니다.
        - 파라미터 누락(rate 없음), 타입 불일치(rate=abc), JSON 형식 오류처럼 Spring 이 자동으로 400 을 내던 경우는
          @ExceptionHandler 로 잡아서 같은 응답 포맷으로 응답하도록 했습니다.

2. 500 Internal Server Error
    * explanation
        - 2-1 합산 조회에서 가격 합계를 Math.addExact 로 계산했습니다.
          그냥 + 로 더하면 int 범위를 넘었을 때 오류 없이 음수 같은 잘못된 값이 나오기 때문에,
          범위를 넘으면 ArithmeticException 을 잡아서 500 으로 응답합니다.
        - 처리하지 못한 나머지 예외도 @ExceptionHandler(Exception.class) 로 잡아서 500 으로 응답합니다.

* @ExceptionHandler 는 ItemController4 안에 작성해서, 이 컨트롤러에서 발생한 예외만 처리하도록 했습니다.


## D. 응답 포맷 통일

* 성공과 실패 응답의 형식을 통일했습니다.

1. 성공

```json
{
  "success": true,
  "data": { "id": 1, "name": "keyboard", "price": 50000 }
}
```

2. 실패

```json
{
  "success": false,
  "error": {
    "code": "ITEM_NOT_FOUND",
    "message": "해당 아이템을 찾을 수 없습니다.",
    "detail": "요청된 ID: 99"
  }
}
```

* explanation
    - 컨트롤러의 success(), created(), error() 메소드로 모든 응답을 만들어서 형식이 항상 같도록 했습니다.
    - LinkedHashMap 을 사용해서 JSON 필드가 넣은 순서대로 출력되도록 했습니다.
    - detail 은 추가 정보가 있을 때만 넣습니다.
    - 미들웨어(401, 503)의 응답도 같은 형식으로 작성했습니다.

* 에러 코드 목록

| 응답 코드 | error.code | 상황 |
|---|---|---|
| 400 | EMPTY_ITEM_LIST | bulk 생성 요청이 빈 배열 |
| 400 | INVALID_ITEM_NAME | name 이 비어 있음 |
| 400 | INVALID_ITEM_PRICE | price 가 없거나 음수 |
| 400 | INVALID_DISCOUNT_RATE | 할인율(rate)이 0 ~ 100 밖 |
| 400 | MISSING_PARAMETER | 필수 파라미터 누락 |
| 400 | INVALID_PARAMETER | 파라미터 형식 오류 (ex. rate=abc) |
| 400 | INVALID_REQUEST_BODY | 요청 바디(JSON) 형식 오류 |
| 401 | INVALID_API_KEY | API 키가 없거나 틀림 |
| 404 | ITEM_NOT_FOUND | 아이템이 없음 |
| 500 | PRICE_SUM_OVERFLOW | 가격 합계가 계산 가능한 범위를 넘음 |
| 500 | INTERNAL_SERVER_ERROR | 그 외 서버 내부 오류 |
| 503 | SERVICE_UNDER_MAINTENANCE | 점검 모드 |


## 실행 및 테스트 방법

1. 실행

```bash
cd springboot-tutorial
./gradlew bootRun                                   # 일반 모드
./gradlew bootRun --args='--app.maintenance=true'   # 점검 모드 (503 테스트용)
```