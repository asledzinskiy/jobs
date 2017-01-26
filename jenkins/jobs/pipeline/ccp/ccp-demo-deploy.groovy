node {
  def dockerRegistry = env.DOCKER_REGISTRY
  def osGerritEndpoint = env.OS_GERRIT_ENDPOINT
  def osCredentialId = env.OS_CREDENTIAL_ID
  def confGerritUrl = env.CONF_GERRIT_URL
  def confGerritCredentialId = env.CONF_GERRIT_CREDENTIAL_ID
  def confEntryPoint = env.CONF_ENTRYPOINT
  def kubernetesUrl = env.KUBERNETES_URL
  def kubernetesCredentialsId = 'kubernetes-api'


  stage('Deploy') {
    build job: 'ccp-docker-deploy', parameters: [
      [$class: 'StringParameterValue', name: 'KUBERNETES_URL', value: kubernetesUrl ],
      [$class: 'StringParameterValue', name: 'CREDENTIALS_ID', value: kubernetesCredentialsId ],
      [$class: 'StringParameterValue', name: 'CONF_GERRIT_URL', value: confGerritUrl ],
      [$class: 'StringParameterValue', name: 'CONF_GERRIT_CREDENTIAL_ID', value: confGerritCredentialId ],
      [$class: 'StringParameterValue', name: 'CONF_ENTRYPOINT', value: confEntryPoint ],
      [$class: 'BooleanParameterValue', name: 'CLEANUP_ENV', value: false ]
    ]
  }
}
