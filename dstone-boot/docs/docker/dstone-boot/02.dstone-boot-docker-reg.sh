#! /bin/sh

# Docker Hub에 로그인
docker login -u jysn007 -p 'db2admin!@'
# 컨테이너 → 이미지 커밋(버전태깅포함)
docker commit dstone-boot dstone-boot:v1.0
# 최종버전태그 추가
docker tag dstone-boot:v1.0 jysn007/dstone-boot:latest
# 두개태그(특정했던버전 + 최종버전) 푸시
docker push jysn007/dstone-boot:v1.0
docker push jysn007/dstone-boot:latest

