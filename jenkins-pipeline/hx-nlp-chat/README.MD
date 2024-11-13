




```bash

ubuntu@k8s-master01:~/test-argocd$ kubectl delete -f argocd-declarative/application-hx-dev-1.yaml
application.argoproj.io "hx-chat-action-server" deleted
ubuntu@k8s-master01:~/test-argocd$ kubectl apply -f argocd-declarative/application-hx-dev-1.yaml
Warning: metadata.finalizers: "resources-finalizer.argocd.argoproj.io": prefer a domain-qualified finalizer name to avoid accidental conflicts with other finalizer writers
application.argoproj.io/hx-chat-action-server-dev-1 created


ubuntu@k8s-master01:~/test-argocd$ kubectl apply -f argocd-declarative/application-hx-staging-1.yaml
application.argoproj.io/hx-chat-action-server-staging-1 configured


```