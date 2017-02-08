def gitTools = new com.mirantis.mcp.Git()
def id = env.GERRIT_CHANGE_NUMBER ?: 'bvt'
def envName = "cicd-${id}-${env.BUILD_ID}"
def deployTimeout = '10'
def registry = env.DOCKER_REGISTRY
def kubernetesURL = env.KUBERNETES_URL

if ( ! env.KUBERNETES_URL ) {
    error("KUBERNETES_URL have to be specified.")
}

node('ccp-docker-build') {
    // get host for k8s url
    URI k8sURL = new URI(kubernetesURL)
    def kubernetesAddress = k8sURL?.getHost()

    try {
        stage('clone fuel-ccp') {
            gitTools.gitSSHCheckout([
                credentialsId : "mcp-ci-gerrit",
                branch : "master",
                host : env.GERRIT_HOST,
                project : "ccp/fuel-ccp"
            ])
        }

        stage('configure kubectl') {
            withCredentials([
                [$class          : 'UsernamePasswordMultiBinding',
                 credentialsId   : 'kubernetes-api',
                 passwordVariable: 'KUBE_PASSWORD',
                 usernameVariable: 'KUBE_LOGIN']
            ]) {
                sh "kubectl config --kubeconfig ${WORKSPACE}/kubeconfig set-credentials kubeuser/kube --username=${KUBE_LOGIN} --password=${KUBE_PASSWORD}"
            }
            sh """
                kubectl config --kubeconfig ${WORKSPACE}/kubeconfig set-cluster kube --insecure-skip-tls-verify=true --server=${kubernetesURL}
                kubectl config --kubeconfig ${WORKSPACE}/kubeconfig set-context my-context --user=kubeuser/kube --cluster=kube
                kubectl config --kubeconfig ${WORKSPACE}/kubeconfig use-context my-context
            """
        }

        def port
        stage('deploy registry') {
            def podFile = "${WORKSPACE}/tools/registry/registry-pod.yaml"
            def serviceFile = "${WORKSPACE}/tools/registry/registry-service.yaml"
            sh """
                kubectl create --kubeconfig ${WORKSPACE}/kubeconfig namespace ${envName}
                sed '/hostPort:/d' -i ${podFile}
                sed '/nodePort:/d' -i ${serviceFile}
                kubectl --namespace ${envName} apply --kubeconfig ${WORKSPACE}/kubeconfig -f ${podFile}
                kubectl --namespace ${envName} apply --kubeconfig ${WORKSPACE}/kubeconfig -f ${serviceFile}
            """
            port = sh(script: "kubectl -n ${envName} describe --kubeconfig ${WORKSPACE}/kubeconfig svc registry | grep 'NodePort:' | grep -Po '[0-9]+'",
                      returnStdout: true).trim()
            echo "Registry port is ${port}."
        }

        stage('build ci images') {
            def jobParameters = [
                [$class: 'StringParameterValue', name: 'DOCKER_REGISTRY', value: "${kubernetesAddress}:${port}" ],
                [$class: 'StringParameterValue', name: 'CONF_GERRIT_URL', value: env.CONF_GERRIT_URL ],
                [$class: 'StringParameterValue', name: 'CONF_ENTRYPOINT', value: env.CONF_ENTRYPOINT ],
                [$class: 'BooleanParameterValue', name: 'USE_REGISTRY_PROXY', value: true ],
            ]
            if ( env.GERRIT_REFSPEC && env.GERRIT_PROJECT ) {
                jobParameters << [$class: 'StringParameterValue', name: 'GERRIT_REFSPEC', value: env.GERRIT_REFSPEC ]
                def component = env.GERRIT_PROJECT.split('/')[-1].minus('fuel-ccp-')
                jobParameters << [$class: 'StringParameterValue', name: 'CCP_COMPONENT', value: component ]
            }
            build job: 'ccp-docker-build', parameters: jobParameters
        }

        // wait for 1 minute, while port used by nginx will be freed from TW state
        sleep(60)

        stage('deploy ci images') {
            build job: 'ccp-docker-deploy', parameters: [
                [$class: 'StringParameterValue', name: 'KUBERNETES_URL', value: kubernetesURL ],
                [$class: 'StringParameterValue', name: 'DOCKER_REGISTRY', value: "${kubernetesAddress}:${port}" ],
                [$class: 'StringParameterValue', name: 'CREDENTIALS_ID', value: 'kubernetes-api' ],
                [$class: 'StringParameterValue', name: 'CONF_GERRIT_URL', value: env.CONF_GERRIT_URL ],
                [$class: 'StringParameterValue', name: 'CONF_ENTRYPOINT', value: env.CONF_ENTRYPOINT ],
                [$class: 'BooleanParameterValue', name: 'USE_REGISTRY_PROXY', value: true ],
                [$class: 'StringParameterValue', name: 'DEPLOY_TIMEOUT', value: deployTimeout ],
            ]
        }
    } catch (err) {
        errMess = err.toString()
        echo errMess
        if ( errMess =~ /\bABORTED\b/ ) {
            currentBuild.result = 'ABORTED'
        } else {
            currentBuild.result = 'FAILED'
        }
    } finally {
        sh """
            kubectl delete --kubeconfig ${WORKSPACE}/kubeconfig ns ${envName} || true
            rm ${WORKSPACE}/kubeconfig
        """
        def k8sCreds = env.K8S_DEPLOYMENT_CREDS
        def common = new com.mirantis.mk.common()
        def osUser = common.getSshCredentials(k8sCreds).getUsername()
        sshagent ([k8sCreds]) {
            sh "ssh -o StrictHostKeyChecking=no -l ${osUser} ${kubernetesAddress} sudo rm -rf /srv/mcp-data/*"
        }
    }
}
