calico = new com.mirantis.mcp.Calico()

node ('calico') {
  try {
    def artifacts = build_cni()
    run_mcp_tests(artifacts)
    def calicoImages = promote_containers(artifacts)
    currentBuild.description = """
    <b>calico/cni</b>: ${calicoImages['cniImage']}<br>
    """
  }
  catch(err) {
    echo "Failed: ${err}"
    currentBuild.result = 'FAILURE'
  }
  finally {
    calico.calicoFixOwnership()
  }
}

def build_cni(){
  dir("${env.WORKSPACE}/cni-plugin") {
    calico.checkoutCalico([
      project_name : 'cni-plugin',
      commit : env.CNI_PLUGIN_COMMIT,
      host : env.GERRIT_HOST,
      credentialsId: 'mcp-ci-gerrit',
    ])
    calico.switchCalicoToDownstreamLibcalicoGo(env.LIBCALICOGO_COMMIT, env.GERRIT_HOST, "./glide.lock")
    calico.testCniPlugin()
    def cniImageData = calico.buildCniPlugin([
      dockerRegistry : env.VIRTUAL_DEV_DOCKER_REGISTRY,
      projectNamespace : env.PROJECT_NAMESPACE,
    ])
    def cniTestImage = calico.publishCalicoImage([
      artifactoryServerName : env.ARTIFACTORY_SERVER,
      dockerRegistry : env.VIRTUAL_DEV_DOCKER_REGISTRY,
      dockerRepo : env.DEV_DOCKER_REGISTRY,
      imageName : cniImageData['cniImage'],
      imageTag : cniImageData['cniImageTag'],
      projectNamespace : env.PROJECT_NAMESPACE,
      publishInfo: false,
    ])
    return [
      cniImage: cniImageData['cniImage'],
      cniImageTag: cniImageData['cniImageTag'],
      CALICO_CNI_IMAGE_REPO: cniTestImage.split(":")[0],
      CALICO_CNI_IMAGE_TAG: cniTestImage.split(":")[1],
    ]
  }
}

def run_mcp_tests(artifacts) {
  stage ("Run system tests") {
    build job: 'calico.system-test.deploy', propagate: true, wait: true, parameters:
      [
        [$class: 'StringParameterValue', name: 'CALICO_CNI_IMAGE_REPO', value: artifacts["CALICO_CNI_IMAGE_REPO"]],
        [$class: 'StringParameterValue', name: 'CALICO_CNI_IMAGE_TAG', value: artifacts["CALICO_CNI_IMAGE_TAG"]],
        [$class: 'StringParameterValue', name: 'OVERWRITE_HYPERKUBE_CNI', value: 'true'],
      ]
  }
}

def promote_containers(artifacts) {
  cniImageProperties = [
    'com.mirantis.targetImg': "${env.PROJECT_NAMESPACE}/${artifacts['cniImage']}",
    'com.mirantis.targetTag': "${artifacts['cniImageTag']}",
  ]
  promoteTag = artifacts['cniImageTag'].split('-[0-9]+$')[0] // remote a timestamp from image tag

  if (env.DOCKER_IMAGE_TAG_SUFFIX) {
    promoteTag = "${promoteTag}-${env.DOCKER_IMAGE_TAG_SUFFIX}"
  }

  calico.promoteCalicoImage([
    imageProperties: cniImageProperties,
    artifactoryServerName : env.ARTIFACTORY_SERVER,
    dockerLookupRepo : env.DEV_DOCKER_REGISTRY,
    dockerPromoteRepo : env.PROD_DOCKER_REGISTRY,
    imageName: 'calico/cni',
    imageTag: promoteTag,
    projectNamespace : env.PROJECT_NAMESPACE,
    defineLatest: false,
  ])

  return [
    cniImage: "${env.VIRTUAL_PROD_DOCKER_REGISTRY}/${env.PROJECT_NAMESPACE}/calico/cni:${promoteTag}",
  ]
}