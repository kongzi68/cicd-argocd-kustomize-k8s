# 实现基于argocd的自动部署

验证基于argocd从代码提交到K8S部署的整个流程实现。

基于gitlab、Jenkins pipeline、kustomize、argocd、K8S。

## 各子文件夹说明

1. hx-kustomize，需要部署的服务kustomize demo
2. jenkins-deploy，用kustomize部署Jenkins-master
3. jenkins-libs，Jenkins共享库
4. jenkins-pipeline，pipeline流水线，实现从代码编译 → 打包镜像 → 修改kustomize → 提交kustomize到gitlab仓库

