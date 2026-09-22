# 자주 쓰는 명령어 모음 — 로컬 / EC2 / MySQL 오가는 법

> "지금 내가 어디에 접속해 있는 거지?"를 매번 헷갈려서 정리한 치트시트.
> 로컬 PC, EC2(운영 서버), 그 안의 MySQL — 이 세 곳을 오갈 때 각각 어떤 터미널에서 뭘 쳐야 하는지가 핵심.

---

## 0. 지금 내가 "어디"에 있는지부터 확인하는 법

터미널 프롬프트만 보면 바로 구분된다.

```
user@DESKTOP ...  C:\Joo_new\jooshop (main)     ← 로컬 PC (PowerShell/Git Bash)
ubuntu@ip-172-31-3-56:~/jooshop$                ← EC2 서버 안 (SSH 접속된 상태)
mysql>                                          ← MySQL 클라이언트 안 (EC2든 로컬이든 한 번 더 들어간 상태)
```

**코드 수정 / git commit·push는 항상 로컬 PC에서만.** EC2 안에서 `git status`를 치면 "로컬에서 방금 고친 코드"가 안 보이는 게 정상이다 — EC2의 코드는 `git pull`을 해야만 최신화되는, 로컬과는 별개의 clone이기 때문.

---

## 1. 로컬 PC → EC2 접속 (SSH)

```bash
# Git Bash 또는 PowerShell
ssh -i <pem키경로> ubuntu@<EC2_퍼블릭_IP>

# 예시
ssh -i ~/keys/jooshop-key.pem ubuntu@43.201.xxx.xxx
```

- Elastic IP를 안 붙였으면 EC2를 Stop→Start 할 때마다 퍼블릭 IP가 바뀐다 — 접속 전에 AWS 콘솔에서 최신 IP 확인.
- `.pem` 키는 최초 발급 시 한 번만 받을 수 있음. 분실하면 그 인스턴스엔 다시 못 들어감.
- 나갈 땐 `exit`.

## 2. EC2 안에서 로컬 MySQL 같은 감각으로 접속하기

EC2에선 mysql이 호스트에 포트를 안 열어놔서(보안), **컨테이너 안으로 한 번 더 들어가야** 한다.

```bash
# 1) 실행 중인 컨테이너 이름 확인
docker ps

# 2) 그 mysql 컨테이너 안으로 접속
docker exec -it <mysql_컨테이너명> mysql -u root -p
# 비밀번호: ~/jooshop/.env 의 MYSQL_PASSWORD 값

# 3) 접속되면
USE shop;
SHOW TABLES;
```

## 3. 로컬 PC에서 로컬 MySQL 접속

로컬은 EC2와 달리 Windows에 직접 설치된 MySQL(포트 3306)이라 컨테이너를 거칠 필요 없이 바로 붙는다.

```bash
mysql -u root -p shop
# 비밀번호: application.yml에 있는 로컬 개발용 값
```

또는 MySQL Workbench / DBeaver 같은 GUI 툴 써도 됨 — `shop` 스키마 열고 쿼리 탭에서 바로 실행.

## 4. 한 줄 요약 — 뭘 어디서 치나

| 하고 싶은 것 | 어디서 | 명령어 |
|---|---|---|
| 코드 수정, git commit/push | 로컬 PC | (에디터 + git) |
| 로컬 DB 확인/수정 | 로컬 PC | `mysql -u root -p shop` |
| 운영 서버 파일/로그 확인 | 로컬 PC → SSH | `ssh -i <pem> ubuntu@<IP>` |
| 운영 DB 확인/수정 | SSH 접속 후 → 컨테이너 진입 | `docker exec -it <mysql컨테이너> mysql -u root -p` |
| 운영 컨테이너 상태/로그 확인 | SSH 접속 후 | `docker compose -f docker-compose.prod.yml ps` 등 |
| 최신 코드로 수동 재배포 | SSH 접속 후 | `git pull` → `docker compose pull` → `up -d` |

---

## 5. Git — 자주 쓰는 것 (로컬 PC 전용)

```bash
# 상태 확인
git status
git status --short

# 절대 git add -A / git add . 하지 말고, 파일 이름 직접 지정해서 스테이징
git add <파일1> <파일2>

# 뭐가 스테이징됐는지 커밋 전에 항상 확인
git diff --cached --stat

# 커밋
git commit -m "타입: 설명"
# 타입 예시 — fix(버그수정) / feat(기능추가) / refactor(구조개선) / chore(잡일/정리) / docs(문서) / test(테스트)

# 푸시 (push하면 GitHub Actions가 자동으로 빌드+EC2 배포까지 실행됨)
git push origin main

# 원격 저장소 이름이 바뀐 뒤로 계속 뜨는 안내문 없애기 (아직 안 함 — 언젠가 해야 함)
git remote set-url origin https://github.com/JOOHD/NewShop.git

# 최근 커밋 로그 확인
git log --oneline -10

# .git/index.lock 에러 날 때 (IntelliJ가 잡고 있을 때 흔함)
rm -f .git/index.lock
```

## 6. Gradle — 자주 쓰는 것 (로컬 PC 전용)

```bash
# 전체 빌드 (컴파일 + 테스트 + jar 생성) — push 전에 미리 돌려서 CI 실패를 로컬에서 미리 거르기
./gradlew build

# 컴파일만 (테스트 스킵, 여러 파일 리네임하고 빠르게 컴파일 에러만 확인할 때)
./gradlew compileJava

# 테스트만
./gradlew test

# 특정 테스트 클래스만
./gradlew test --tests "JOO.jooshop.payment.service.PaymentServiceTest"

# 캐시 꼬였을 때 클린 빌드
./gradlew clean build
```

## 7. Docker Compose — 로컬 vs 운영

```bash
# ── 로컬 (docker-compose.yml, 파일명 생략 가능) ──
docker compose up --build       # 이미지 새로 빌드하면서 기동
docker compose up -d            # 백그라운드로 기동
docker compose stop             # 멈추기만, 데이터 유지
docker compose down             # 컨테이너+네트워크 삭제 (volume=DB데이터는 유지)
docker compose down -v          # DB 데이터까지 완전 초기화 (주의)

# ── 운영 (docker-compose.prod.yml, -f로 반드시 파일 지정) ──
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml pull app
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml logs -f app
```

## 8. Docker 디버깅 — 운영 서버에서 뭔가 안 될 때 (SSH 접속 후)

```bash
docker ps                                         # 컨테이너 목록/상태
docker logs <컨테이너명> --tail 50                  # 최근 로그
docker logs <컨테이너명> 2>&1 | grep -i error       # 에러만 뽑기
docker inspect --format='OOMKilled={{.State.OOMKilled}}' <컨테이너명>  # 메모리 부족으로 죽었는지
df -h /                                            # 디스크 용량 확인 (100%면 배포가 조용히 실패함)
docker image prune -af                             # 안 쓰는 이미지 싹 정리 (디스크 부족할 때)
```

---

## 참고

- 세부 원리/에러별 원인 설명은 [`DEPLOY_EC2.md`](./DEPLOY_EC2.md)의 12번(운영 서버 디버깅 명령어 모음) 항목에 더 자세히 있음 — 이 파일은 "원리 설명"보다 "지금 당장 뭘 쳐야 하는지"에 집중한 빠른 참조용.
- DB 관련 트러블슈팅(`ddl-auto` 함정, 컬럼/테이블 리네임 등)은 [`../TROUBLESHOOTING.md`](../TROUBLESHOOTING.md) 참고.
