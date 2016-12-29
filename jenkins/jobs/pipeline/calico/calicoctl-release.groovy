calico = new com.mirantis.mcp.Calico()
artifactory = new com.mirantis.mcp.MCPArtifactory()

artifactoryServer = Artifactory.server(env.ARTIFACTORY_SERVER)
artifactoryURL = "${artifactoryServer.getUrl()}/${env.TEST_BINARY_REPO}"

node ('calico') {
  try {
    def birdBuildId = prepare_bird()
    def confdBuildId = prepare_confd()
    def buildImage = prepare_libcalico()
    def felixImage = prepare_felix()
    def artifacts = build_containers(buildImage, felixImage, confdBuildId, birdBuildId)
    run_mcp_tests(artifacts)
    def calicoImages = promote_containers(artifacts)

    currentBuild.description = """
    <b>calico/node</b>: ${calicoImages['nodeImage']}<br>
    <b>calico/ctl</b>: ${calicoImages['ctlImage']}<br><br>
    <i>calico/build</i>: ${buildImage}<br>
    <i>calico/felix</i>: ${felixImage}<br>
    <i>Bird build ID</i>: ${birdBuildId}<br>
    <i>Confd build ID</i>: ${confdBuildId}<br>
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

def prepare_libcalico() {
  dir("${env.WORKSPACE}/libcalico") {
    calico.checkoutCalico([
      project_name : 'libcalico',
      commit : env.LIBCALICO_COMMIT,
      host : env.GERRIT_HOST,
      credentialsId: 'mcp-ci-gerrit',
    ])
    calico.testLibcalico()
    def buildImageData = calico.buildLibcalico([
      dockerRegistry : env.TEST_DOCKER_REGISTRY,
      projectNamespace : env.PROJECT_NAMESPACE,
    ])
    return calico.publishCalicoImage([
      artifactoryServerName : env.ARTIFACTORY_SERVER,
      dockerRegistry : env.TEST_DOCKER_REGISTRY,
      imageName : buildImageData['buildImage'],
      imageTag : buildImageData['buildImageTag'],
      projectNamespace : env.PROJECT_NAMESPACE,
      publishInfo: false,
    ])
  }
}

def prepare_bird() {
  dir("${env.WORKSPACE}/bird") {
    calico.checkoutCalico([
      project_name : 'bird',
      commit : env.BIRD_COMMIT,
      host : env.GERRIT_HOST,
      credentialsId: 'mcp-ci-gerrit',
    ])
    calico.buildCalicoBird()
    return calico.publishCalicoBird([
      artifactoryServerName : env.ARTIFACTORY_SERVER,
      binaryRepo : env.TEST_BINARY_REPO,
      projectNamespace : env.PROJECT_NAMESPACE,
      publishInfo : false
    ])
  }
}

def prepare_confd() {
  dir("${env.WORKSPACE}/confd") {
    calico.checkoutCalico([
      project_name : 'confd',
      commit : env.CONFD_COMMIT,
      host : env.GERRIT_HOST,
      credentialsId: 'mcp-ci-gerrit',
    ])
    calico.testCalicoConfd()
    calico.buildCalicoConfd()
    return calico.publishCalicoConfd([
      artifactoryServerName : env.ARTIFACTORY_SERVER,
      binaryRepo : env.TEST_BINARY_REPO,
      projectNamespace: env.PROJECT_NAMESPACE,
      publishInfo : false
    ])
  }
}

def prepare_felix() {
  dir("${env.WORKSPACE}/felix") {
    calico.checkoutCalico([
      project_name : 'felix',
      commit : env.FELIX_COMMIT,
      host : env.GERRIT_HOST,
      credentialsId: 'mcp-ci-gerrit',
    ])
    calico.switchCalicoToDownstreamLibcalicoGo(env.LIBCALICOGO_COMMIT, env.GERRIT_HOST, "./go/glide.lock")
    calico.testFelix()
    def felixImageData = calico.buildFelix([
      dockerRegistry : env.TEST_DOCKER_REGISTRY,
      projectNamespace : env.PROJECT_NAMESPACE,
    ])
    return calico.publishCalicoImage([
      artifactoryServerName : env.ARTIFACTORY_SERVER,
      dockerRegistry : env.TEST_DOCKER_REGISTRY,
      imageName : felixImageData['felixImage'],
      imageTag : felixImageData['felixImageTag'],
      projectNamespace : env.PROJECT_NAMESPACE,
      publishInfo: false,
    ])
  }
}

def build_containers(String buildImage, String felixImage, String confdBuildId, String birdBuildId){
  dir("${env.WORKSPACE}/calicoctl") {
    calico.checkoutCalico([
      project_name : 'calicoctl',
      commit : env.CALICOCTL_COMMIT,
      host : env.GERRIT_HOST,
      credentialsId: 'mcp-ci-gerrit',
    ])
    calico.testCalicoctl()
    def calicoContainersArts = calico.buildCalicoContainers([
      dockerRegistry : env.TEST_DOCKER_REGISTRY,
      artifactoryURL : artifactoryURL,
      projectNamespace : env.PROJECT_NAMESPACE,
      buildImage : buildImage,
      felixImage : felixImage,
      confdBuildId : confdBuildId,
      birdBuildId : birdBuildId,
    ])
    calico.publishCalicoImage([
      artifactoryServerName : env.ARTIFACTORY_SERVER,
      dockerRegistry : env.TEST_DOCKER_REGISTRY,
      imageName : calicoContainersArts['NODE_CONTAINER_NAME'],
      imageTag : calicoContainersArts['CALICO_VERSION'],
      projectNamespace : env.PROJECT_NAMESPACE,
      publishInfo: false,
    ])
    calico.publishCalicoImage([
      artifactoryServerName : env.ARTIFACTORY_SERVER,
      dockerRegistry : env.TEST_DOCKER_REGISTRY,
      imageName : calicoContainersArts['CTL_CONTAINER_NAME'],
      imageTag : calicoContainersArts['CALICO_VERSION'],
      projectNamespace : env.PROJECT_NAMESPACE,
      publishInfo: false,
    ])
    return calicoContainersArts
  }
}

def run_mcp_tests(artifacts) {
  stage ("Run system tests") {
     build job: 'calico.system-test.deploy', propagate: true, wait: true, parameters:
      [
        [$class: 'StringParameterValue', name: 'CALICO_NODE_IMAGE_REPO', value: artifacts["CALICO_NODE_IMAGE_REPO"]],
        [$class: 'StringParameterValue', name: 'CALICOCTL_IMAGE_REPO', value: artifacts["CALICOCTL_IMAGE_REPO"]],
        [$class: 'StringParameterValue', name: 'CALICO_VERSION', value: artifacts["CALICO_VERSION"]],
      ]
  }
}

def promote_containers(artifacts) {
  nodeImageProperties = [
    'com.mirantis.targetImg': "${env.PROJECT_NAMESPACE}/${artifacts['NODE_CONTAINER_NAME']}",
    'com.mirantis.targetTag': "${artifacts['CALICO_VERSION']}",
  ]
  ctlImageProperties = [
    'com.mirantis.targetImg': "${env.PROJECT_NAMESPACE}/${artifacts['CTL_CONTAINER_NAME']}",
    'com.mirantis.targetTag': "${artifacts['CALICO_VERSION']}",
  ]
  promoteTag = artifacts['CALICO_VERSION'].split('-[0-9]+$')[0] // remote a timestamp from image tag

  calico.promoteCalicoImage([
    imageProperties: nodeImageProperties,
    artifactoryServerName : env.ARTIFACTORY_SERVER,
    dockerLookupRepo : env.TEST_DOCKER_REGISTRY,
    dockerPromoteRepo : env.PROD_DOCKER_REGISTRY,
    imageName: 'calico/node',
    imageTag: promoteTag,
    defineLatest: true
  ])
  calico.promoteCalicoImage([
    imageProperties: ctlImageProperties,
    artifactoryServerName : env.ARTIFACTORY_SERVER,
    dockerLookupRepo : env.TEST_DOCKER_REGISTRY,
    dockerPromoteRepo : env.PROD_DOCKER_REGISTRY,
    imageName: 'calico/ctl',
    imageTag: promoteTag,
    defineLatest: true
  ])
  return [
    nodeImage: "${env.PROD_DOCKER_REGISTRY}/${env.PROJECT_NAMESPACE}/calico/node:${promoteTag}",
    ctlImage: "${env.PROD_DOCKER_REGISTRY}/${env.PROJECT_NAMESPACE}/calico/ctl:${promoteTag}",
  ]
}