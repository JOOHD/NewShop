<svg viewBox="0 0 780 980" xmlns="http://www.w3.org/2000/svg" font-family="monospace" font-size="13">
  <title>Exception Flow Diagram</title>
  <desc>MemberNotFoundException이 던져져서 클라이언트 응답까지 가는 전체 흐름</desc>

  <!-- BG -->
  <rect width="780" height="980" fill="#0f1117"/>

  <!-- STEP 1 -->
  <rect x="30" y="30" width="720" height="110" rx="10" fill="#1a1d27" stroke="#5c6bc0" stroke-width="1.5"/>
  <text x="50" y="56" fill="#7986cb" font-size="11" font-weight="bold">① Service — 예외 던짐</text>
  <text x="50" y="80" fill="#8b8fa8">memberRepository.findById(999L)</text>
  <text x="50" y="100" fill="#555870">    .orElseThrow(</text>
  <text x="50" y="118" fill="#66bb6a">        () → new MemberNotFoundException()  </text>
  <text x="430" y="118" fill="#555870">// id=999 없으면 throw</text>
  <text x="50" y="136" fill="#555870">    );</text>

  <line x1="390" y1="142" x2="390" y2="175" stroke="#555870" stroke-width="1.5" marker-end="url(#arr)"/>
  <text x="400" y="164" fill="#555870" font-size="11">throw 발생</text>

  <!-- STEP 2 -->
  <rect x="30" y="177" width="720" height="140" rx="10" fill="#1a1d27" stroke="#ffa726" stroke-width="1.5"/>
  <text x="50" y="203" fill="#ffa726" font-size="11" font-weight="bold">② MemberNotFoundException 생성자 실행</text>
  <text x="50" y="228" fill="#8b8fa8">class MemberNotFoundException extends BusinessException {</text>
  <text x="50" y="248" fill="#8b8fa8">    MemberNotFoundException() {</text>
  <text x="50" y="268" fill="#66bb6a">        super(ErrorCode.MEMBER_NOT_FOUND);  </text>
  <text x="388" y="268" fill="#555870">// 부모 생성자로 전달</text>
  <text x="50" y="288" fill="#8b8fa8">    }</text>
  <text x="50" y="308" fill="#8b8fa8">}</text>

  <line x1="390" y1="319" x2="390" y2="352" stroke="#555870" stroke-width="1.5" marker-end="url(#arr)"/>
  <text x="400" y="340" fill="#555870" font-size="11">super() 호출</text>

  <!-- STEP 3 -->
  <rect x="30" y="354" width="720" height="160" rx="10" fill="#1a1d27" stroke="#ffa726" stroke-width="1.5"/>
  <text x="50" y="380" fill="#ffa726" font-size="11" font-weight="bold">③ BusinessException 생성자 실행</text>
  <text x="50" y="405" fill="#8b8fa8">class BusinessException extends RuntimeException {</text>
  <text x="50" y="425" fill="#8b8fa8">    private final ErrorCode errorCode;</text>
  <text x="50" y="450" fill="#8b8fa8">    BusinessException(ErrorCode errorCode) {</text>
  <text x="50" y="470" fill="#66bb6a">        super("회원을 찾을 수 없습니다.");  </text>
  <text x="330" y="470" fill="#555870">// RuntimeException에 메시지 저장</text>
  <text x="50" y="490" fill="#66bb6a">        this.errorCode = MEMBER_NOT_FOUND; </text>
  <text x="338" y="490" fill="#555870">// 필드에 보관</text>
  <text x="50" y="508" fill="#8b8fa8">    }</text>

  <line x1="390" y1="516" x2="390" y2="549" stroke="#555870" stroke-width="1.5" marker-end="url(#arr)"/>
  <text x="400" y="537" fill="#555870" font-size="11">Spring이 감지 → 핸들러로</text>

  <!-- STEP 4 -->
  <rect x="30" y="551" width="720" height="170" rx="10" fill="#1a1d27" stroke="#5c6bc0" stroke-width="1.5"/>
  <text x="50" y="577" fill="#7986cb" font-size="11" font-weight="bold">④ GlobalExceptionHandler — 잡음</text>
  <text x="50" y="602" fill="#8b8fa8">@ExceptionHandler(BusinessException.class)  </text>
  <text x="370" y="602" fill="#555870">// MemberNotFoundException도 여기서 잡힘</text>
  <text x="50" y="622" fill="#8b8fa8">ResponseEntity handle(BusinessException ex) {</text>
  <text x="50" y="647" fill="#66bb6a">    ErrorCode code = ex.getErrorCode();  </text>
  <text x="314" y="647" fill="#555870">// → MEMBER_NOT_FOUND 꺼냄</text>
  <text x="50" y="667" fill="#66bb6a">    // code.getStatus()  → 404</text>
  <text x="50" y="687" fill="#66bb6a">    // code.getMessage() → "회원을 찾을 수 없습니다."</text>
  <text x="50" y="707" fill="#8b8fa8">}</text>

  <line x1="390" y1="723" x2="390" y2="756" stroke="#555870" stroke-width="1.5" marker-end="url(#arr)"/>
  <text x="400" y="744" fill="#555870" font-size="11">ErrorResponse 조립</text>

  <!-- STEP 5 -->
  <rect x="30" y="758" width="720" height="130" rx="10" fill="#1a1d27" stroke="#26a69a" stroke-width="1.5"/>
  <text x="50" y="784" fill="#4db6ac" font-size="11" font-weight="bold">⑤ ErrorResponse.from(code) — 응답 조립</text>
  <text x="50" y="809" fill="#8b8fa8">ErrorResponse.from(MEMBER_NOT_FOUND)</text>
  <text x="50" y="829" fill="#66bb6a">→ status  = 404</text>
  <text x="50" y="849" fill="#66bb6a">→ error   = "Not Found"   </text>
  <text x="222" y="849" fill="#555870">// HttpStatus.valueOf(404).getReasonPhrase()</text>
  <text x="50" y="869" fill="#66bb6a">→ message = "회원을 찾을 수 없습니다."</text>

  <line x1="390" y1="890" x2="390" y2="920" stroke="#555870" stroke-width="1.5" marker-end="url(#arr)"/>

  <!-- STEP 6 -->
  <rect x="30" y="922" width="720" height="46" rx="10" fill="#1b4d48" stroke="#26a69a" stroke-width="1.5"/>
  <text x="50" y="942" fill="#4db6ac" font-size="11" font-weight="bold">⑥ 클라이언트 응답</text>
  <text x="50" y="959" fill="#e0e0e0">HTTP 404  ·  { "status": 404, "error": "Not Found", "message": "회원을 찾을 수 없습니다." }</text>

  <defs>
    <marker id="arr" markerWidth="8" markerHeight="8" refX="4" refY="4" orient="auto">
      <path d="M0,0 L8,4 L0,8 Z" fill="#555870"/>
    </marker>
  </defs>
</svg>
