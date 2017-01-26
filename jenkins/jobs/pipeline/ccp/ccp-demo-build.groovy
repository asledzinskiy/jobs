node {
  def dockerRegistry = env.DOCKER_REGISTRY
  def osGerritEndpoint = env.OS_GERRIT_ENDPOINT
  def osCredentialId = env.OS_CREDENTIAL_ID
  def confGerritUrl = env.CONF_GERRIT_URL
  def confGerritCredentialId = env.CONF_GERRIT_CREDENTIAL_ID
  def confEntryPoint = env.CONF_ENTRYPOINT
  def kubernetesUrl = env.KUBERNETES_URL
  def kubernetesCredentialsId = 'kubernetes-api'

  stage('Build') {
    build job: 'ccp-docker-build', parameters: [
      [$class: 'StringParameterValue', name: 'DOCKER_REGISTRY', value: dockerRegistry ],
      [$class: 'StringParameterValue', name: 'OS_GERRIT_ENDPOINT', value: osGerritEndpoint ],
      [$class: 'StringParameterValue', name: 'OS_CREDENTIAL_ID', value: osCredentialId ],
      [$class: 'StringParameterValue', name: 'CONF_GERRIT_URL', value: confGerritUrl ],
      [$class: 'StringParameterValue', name: 'CONF_GERRIT_CREDENTIAL_ID', value: confGerritCredentialId ],
      [$class: 'StringParameterValue', name: 'CONF_ENTRYPOINT', value: confEntryPoint ]
    ]
  }

}
