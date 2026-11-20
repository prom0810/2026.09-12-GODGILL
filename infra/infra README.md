# 인프라·배포 가이드

갓길(GODGILL) 백엔드를 Kubernetes(kubeadm) 클러스터에 배포하고 운영하는 방법을 정리한 문서입니다.
이 문서에는 비밀번호, 토큰, 키 값을 적지 않습니다. 비밀 값은 클러스터 Secret에서만 관리합니다.

## 1. 전체 구조

```
개발자 PR 머지(main)
   └─ GitHub Actions(Backend Image): backend/Dockerfile로 이미지 빌드
        └─ GHCR: ghcr.io/prom0810/godgill-backend:sha-<커밋7자리>
             └─ infra/k8s/backend.yaml의 이미지 태그 변경(PR)
                  └─ k8s-cp에서 git pull + kubectl apply
                       └─ Deployment(godgill-backend) → NodePort 30080 → 앱
                            └─ Supabase PostgreSQL(세션 풀러)
```

| 구성 요소 | 내용 |
|---|---|
| 클러스터 | kubeadm 기반 Kubernetes v1.36 (컨트롤 플레인 1 + 워커 2), VirtualBox VM 3대 |
| OS / 런타임 | Ubuntu 22.04, containerd |
| 네트워크 플러그인 | Flannel (Pod CIDR 10.244.0.0/16, `--iface=enp0s8`) |
| 컨테이너 이미지 | GitHub Actions가 빌드해 GHCR(공개 패키지)에 저장 |
| 외부 접속 | Service NodePort 30080 (Ingress 컨트롤러 없음) |
| DB | Supabase PostgreSQL (세션 풀러, 연결 한도 15) |

## 2. 노드와 접속

| 노드 | 역할 | 호스트 전용 IP | PuTTY 접속 (학교 PC 기준) |
|---|---|---|---|
| k8s-cp | 컨트롤 플레인 | 192.168.56.10 | 127.0.0.1 : 2210 |
| k8s-w1 | 워커 1 | 192.168.56.11 | 127.0.0.1 : 2211 |
| k8s-w2 | 워커 2 | 192.168.56.12 | 127.0.0.1 : 2212 |

- 각 VM은 어댑터 2개를 사용합니다. 어댑터 1(NAT)은 인터넷과 포트포워딩용, 어댑터 2(호스트 전용)는 노드 간 통신용입니다.
- NAT IP는 모든 VM이 10.0.2.15로 같기 때문에, kubelet은 `--node-ip`로 호스트 전용 IP를 쓰도록 설정되어 있습니다.
- `kubectl`은 k8s-cp에서 실행합니다.

### 앱에서 서버에 접속하기

k8s-w1의 VirtualBox 포트포워딩 규칙 `api`(호스트 8080 → 게스트 10.0.2.15:30080)로 학교 PC의 8080 포트가 NodePort에 연결됩니다.

- 서버 주소: `http://<학교 PC의 IPv4>:8080`
- 앱 `android/local.properties`에 `BACKEND_BASE_URL=http://<학교 PC의 IPv4>:8080`을 추가하고 다시 빌드합니다. (이 파일은 커밋하지 않습니다.)
- 학교 PC와 같은 네트워크에 있어야 접속됩니다. 학교 PC가 꺼져 있거나 VM이 꺼져 있으면 서버도 내려갑니다.
- 학교 PC의 IP는 공유기가 부여한 값이라 재부팅하면 바뀔 수 있습니다.
- 디버그 빌드만 HTTP 접속이 허용됩니다. CI에서 받는 APK는 HTTPS 주소만 받습니다.

## 3. 클러스터 켜기 / 끄기

켜기: VirtualBox에서 **k8s-cp → k8s-w1 → k8s-w2** 순서로 시작하고 1~2분 기다립니다.

```bash
kubectl get nodes      # 3대 모두 Ready
kubectl get pods       # godgill-backend 1/1 Running (자동으로 다시 올라옴)
```

끄기: 각 VM에서 `sudo poweroff`를 실행합니다. 워커 → 컨트롤 플레인 순서로 끕니다. VirtualBox 창을 X로 닫지 않습니다.

## 4. 배포 절차

코드 변경부터 클러스터 반영까지의 순서입니다.

1. 백엔드 코드를 PR로 `main`에 머지합니다.
2. GitHub **Actions** 탭에서 `Backend Image`가 성공하면 이미지가 만들어집니다. 실행 요약에 이미지 태그(`sha-xxxxxxx`)가 표시됩니다.
3. `infra/k8s/backend.yaml`의 `image:` 줄을 새 태그로 바꾸는 PR을 올리고 머지합니다. (`latest`는 쓰지 않습니다. 배포된 버전을 추적하기 위해서입니다.)
4. k8s-cp에서 반영합니다.

```bash
cd ~/2026.09-12-GODGILL
git pull
kubectl apply -f infra/k8s/backend.yaml
kubectl rollout status deploy/godgill-backend
kubectl get pods -o wide
```

배포 전략이 `Recreate`라서 교체되는 동안 수십 초간 접속이 끊깁니다. (Supabase 세션 풀러의 연결 수 한도를 지키기 위한 설정입니다.)

### 동작 확인

```bash
curl -i http://192.168.56.11:30080/                 # 404 JSON("존재하지 않는 API입니다.")이면 서버 정상
kubectl logs deploy/godgill-backend --tail=30       # 서버 로그
```

### 롤백

```bash
kubectl rollout undo deploy/godgill-backend
```

레포의 매니페스트와 클러스터 상태가 달라지므로, 롤백 후에는 매니페스트도 이전 태그로 되돌리는 PR을 올립니다.

## 5. 설정과 Secret

백엔드는 Secret `godgill-backend`의 모든 키를 환경 변수로 주입받습니다. 값은 레포에 올리지 않습니다.

| 키 | 용도 | 상태 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | DB JDBC URL (Supabase 세션 풀러) | 적용됨 |
| `SPRING_DATASOURCE_USERNAME` | DB 사용자 (`postgres.<프로젝트ref>`) | 적용됨 |
| `SPRING_DATASOURCE_PASSWORD` | DB 비밀번호 | 적용됨 |
| `JWT_SECRET` | 로그인 토큰 서명 키 | 적용 예정 (현재 코드 기본값 사용 중) |
| `STORAGE_ENDPOINT`, `STORAGE_REGION`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, `STORAGE_BUCKET` | Supabase Storage(S3 호환) | 적용 예정 |

Secret 값을 추가하거나 바꾸는 방법입니다. 값은 `read`로 입력해서 화면과 기록에 남기지 않습니다.

```bash
read -s VALUE
kubectl patch secret godgill-backend -p "{\"stringData\":{\"KEY_NAME\":\"$VALUE\"}}"
unset VALUE
```

Secret을 바꾼 뒤에는 파드를 새로 띄워야 반영됩니다.

```bash
kubectl rollout restart deploy/godgill-backend
```

Deployment의 주요 값은 `infra/k8s/backend.yaml`에 있습니다.

| 값 | 설명 |
|---|---|
| `replicas: 1` | 늘릴 때는 `replicas × Hikari 풀(3)`이 Supabase 한도 15 이하가 되도록 맞춥니다. |
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=3` | 파드당 DB 연결 수 |
| `STORAGE_TYPE=local` | 제보 이미지 저장 방식. `supabase`로 바꾸기 전까지 파드 재시작 시 이미지가 사라집니다. |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError` |
| 메모리 | requests 384Mi / limits 1Gi |

## 6. 문제 해결

| 증상 | 확인 |
|---|---|
| 노드가 NotReady | `kubectl -n kube-flannel get pods`, 해당 노드의 `sudo systemctl status kubelet` |
| 노드의 INTERNAL-IP가 10.0.2.15 | `/etc/default/kubelet`의 `--node-ip` 확인 후 `sudo systemctl restart kubelet` |
| `ImagePullBackOff` | `kubectl describe pod -l app=godgill-backend`. GHCR 패키지가 Public인지, 이미지 태그가 맞는지 확인 |
| `CrashLoopBackOff` | `kubectl logs deploy/godgill-backend --tail=50`. DB 접속 정보(Secret) 확인 |
| 앱에서 접속 안 됨 | 학교 PC 브라우저에서 `http://<IPv4>:8080/` 확인, VirtualBox `api` 포워딩 규칙 확인, 같은 네트워크인지 확인 |
| 학교 PC `ipconfig`가 안 됨 | PATH 문제입니다. 전체 경로 `C:\Windows\System32\ipconfig.exe`로 실행 |
| 8080이 이미 사용 중 | `C:\Windows\System32\netstat.exe -ano`로 확인하고, 포워딩 호스트 포트를 바꿈 (예: 18080) |

조인 토큰이 만료됐을 때(워커를 다시 추가할 때):

```bash
kubeadm token create --print-join-command     # k8s-cp에서 실행하고 출력된 명령을 워커에서 sudo로 실행
```

## 7. 클러스터 구축 요약 (재현용)

각 단계는 Ubuntu 22.04 VM에서 진행했습니다. 자세한 옵션은 공식 문서를 따릅니다.

1. **공통 설정(전 노드)**: swap 영구 비활성화(`/etc/fstab`의 swap 줄 주석 처리), `overlay`·`br_netfilter` 모듈과 sysctl(`ip_forward` 등) 설정
2. **containerd 설치**: `SystemdCgroup = true`로 설정
3. **kubeadm / kubelet / kubectl 설치**: pkgs.k8s.io의 v1.36 저장소 사용, `apt-mark hold`로 버전 고정
4. **VM 복제 후 노드별 설정**: 호스트명, 호스트 전용 IP(`/etc/netplan`), `machine-id`와 SSH 호스트 키 재생성, kubelet `--node-ip`
5. **컨트롤 플레인 초기화(k8s-cp)**:
   ```bash
   sudo kubeadm init --apiserver-advertise-address=192.168.56.10 --pod-network-cidr=10.244.0.0/16
   ```
6. **Flannel 설치**: 매니페스트의 `kube-flannel` 컨테이너 args에 `--iface=enp0s8` 추가
7. **워커 합류**: `kubeadm join ...` (k8s-w1, k8s-w2)
8. **Secret 생성 후 `kubectl apply -f infra/k8s/backend.yaml`**

## 8. 알려진 제한과 후속 작업

- [ ] 제보 이미지 저장소를 Supabase Storage로 전환 (`STORAGE_TYPE=supabase`)
- [ ] `JWT_SECRET`을 운영용 랜덤 값으로 교체
- [ ] 푸시 알림(FCM): `FIREBASE_CREDENTIALS_PATH`가 비어 있어 발송 내용이 서버 로그에만 남음
- [ ] 클러스터 자동 반영(CD): 현재는 이미지 태그 변경 PR과 `kubectl apply`를 수동으로 실행
- [ ] 학교 PC IP 고정 (공유기 DHCP 예약)
- [ ] 장애 복구 시험(파드 삭제, 워커 노드 중단) 결과 기록
- 파드가 1개라 단일 장애 지점입니다. 연결 수 한도(Supabase 15)를 고려해 늘려야 합니다.
