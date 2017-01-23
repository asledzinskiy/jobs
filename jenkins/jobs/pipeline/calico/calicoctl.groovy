// Add library functions from pipeline-library
artifactory = new com.mirantis.mcp.MCPArtifactory()
calico = new com.mirantis.mcp.Calico()
common = new com.mirantis.mcp.Common()
docker = new com.mirantis.mcp.Docker()
git = new com.mirantis.mcp.Git()
// Artifactory server
artifactoryServer = Artifactory.server("mcp-ci")
buildInfo = Artifactory.newBuildInfo()

projectNamespace = "mirantis/projectcalico"
docker_dev_repo = "docker-dev-local"
docker_prod_repo = "docker-prod-local"

label = "buildpod.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
jenkinsSlaveImg = 'docker-prod-virtual.docker.mirantis.net/mirantis/jenkins-slave-images/projectcalico-debian-slave-20161223134732:latest'
jnlpSlaveImg = 'docker-prod-virtual.docker.mirantis.net/mirantis/jenkins-slave-images/jnlp-slave:latest'

if ( env.GERRIT_EVENT_TYPE == 'patchset-created' ) {
    buildCalicoContainers()
} else if ( env.GERRIT_EVENT_TYPE == 'change-merged' ) {
    promote_artifacts()
} else {
  throw new RuntimeException("Job should be triggered only on patchset-created or change-merged events")
}

def buildCalicoContainers(){

  podTemplate(label: label,
    containers: [
        containerTemplate(
            name: 'jnlp',
            image: jnlpSlaveImg,
            args: '${computer.jnlpmac} ${computer.name}'
        ),
        containerTemplate(
            name: 'calico-slave',
            image: jenkinsSlaveImg,
            alwaysPullImage: false,
            ttyEnabled: true,
            privileged: true
        )
    ],
    ) {
    node(label){
      container('calico-slave') {
        try {

          stage ('Checkout calicoctl'){
            git.gerritPatchsetCheckout ([
              credentialsId : "mcp-ci-gerrit",
              withWipeOut : true
            ])
          }

          stage ('Run unittest') { sh "make test-containerized"  }

          // start building calicoctl
          def artifactoryUrl = artifactoryServer.getUrl()
          def dockerRepository = env.DOCKER_REGISTRY
          def nodeImg = "${dockerRepository}/${projectNamespace}/calico/node"
          def ctlImg = "${dockerRepository}/${projectNamespace}/calico/ctl"
          def calicoContainersArts = calico.buildCalicoContainers {
            artifactoryURL = "${artifactoryUrl}/binary-prod-virtual"
            dockerRepo = dockerRepository
            nodeImage = nodeImg
            ctlImage = ctlImg
          }

          def calicoImgTag = calicoContainersArts["CALICO_VERSION"]

          stage('Publishing containers artifacts') {
            artifactory.uploadImageToArtifactory(artifactoryServer,
                                                 dockerRepository,
                                                 "${projectNamespace}/calico/node",
                                                 calicoImgTag,
                                                 docker_dev_repo)
            artifactory.uploadImageToArtifactory(artifactoryServer,
                                                 dockerRepository,
                                                 "${projectNamespace}/calico/ctl",
                                                 calicoImgTag,
                                                 docker_dev_repo)
          } // publishing artifacts

          currentBuild.description = """
            <b>node</b>: ${nodeImg}:${calicoImgTag}<br>
            <b>ctl</b>: ${ctlImg}:${calicoImgTag}<br>
            """
          stage ("Run system tests") {
             // build job: 'calico.system-test.deploy', propagate: true, wait: true, parameters:
             //  [
             //      [$class: 'StringParameterValue', name: 'CALICO_NODE_IMAGE_REPO', value: calicoContainersArts["CALICO_NODE_IMAGE_REPO"]],
             //      [$class: 'StringParameterValue', name: 'CALICOCTL_IMAGE_REPO', value: calicoContainersArts["CALICOCTL_IMAGE_REPO"]],
             //      [$class: 'StringParameterValue', name: 'CALICO_VERSION', value: calicoImgTag],
             //      [$class: 'StringParameterValue', name: 'MCP_BRANCH', value: 'mcp'],
             //  ]
             echo "Done"
          }

        }
        catch(err) {
          echo "Failed: ${err}"
          currentBuild.result = 'FAILURE'
        }
        finally {
          // fix workspace owners
          sh "sudo chown -R jenkins:jenkins ${env.WORKSPACE} ${env.HOME}/.glide || true"
        }
      } // container
    } // node
  } // podTemplate

} // buildCalicoContainers


def promote_artifacts () {
  podTemplate(label: label,
    containers: [
        containerTemplate(
            name: 'jnlp',
            image: jnlpSlaveImg,
            args: '${computer.jnlpmac} ${computer.name}'
        )
    ],
    ) {
    node(label) {
      container('jnlp') {
        stage('promote') {

          // search calicoctl artifacts
          def calicoProperties = [
            'com.mirantis.gerritChangeId': "${env.GERRIT_CHANGE_ID}",
            'com.mirantis.gerritPatchsetNumber': "${env.GERRIT_PATCHSET_NUMBER}",
            'com.mirantis.gerritChangeNumber' : "${env.GERRIT_CHANGE_NUMBER}"
          ]
          // Search for an artifact with required properties
          def artifactURI = artifactory.uriByProperties(artifactoryServer.getUrl(), calicoProperties)
          // Get build info: build id and job name
          if ( artifactURI ) {
              def buildProperties = artifactory.getPropertiesForArtifact(artifactURI)
                //promote calico/ctl image
                artifactory.promoteDockerArtifact(artifactoryServer.getUrl(),
                        docker_dev_repo,
                        docker_prod_repo,
                        "${projectNamespace}/calico/ctl",
                        buildProperties.get('com.mirantis.targetTag').join(','),
                        buildProperties.get('com.mirantis.targetTag').join(','),
                        true)
                artifactory.promoteDockerArtifact(artifactoryServer.getUrl(),
                        docker_dev_repo,
                        docker_prod_repo,
                        "${projectNamespace}/calico/ctl",
                        buildProperties.get('com.mirantis.targetTag').join(','),
                        'latest')
                //promote calico/node image
                artifactory.promoteDockerArtifact(artifactoryServer.getUrl(),
                        docker_dev_repo,
                        docker_prod_repo,
                        "${projectNamespace}/calico/node",
                        buildProperties.get('com.mirantis.targetTag').join(','),
                        buildProperties.get('com.mirantis.targetTag').join(','),
                        true)
                artifactory.promoteDockerArtifact(artifactoryServer.getUrl(),
                        docker_dev_repo,
                        docker_prod_repo,
                        "${projectNamespace}/calico/node",
                        buildProperties.get('com.mirantis.targetTag').join(','),
                        'latest')
            } else {
                throw new RuntimeException("Artifacts were not found, nothing to promote")
            }
        } // stage promote
      } // container
    } // node
  } // podTemplate
} // promote_artifacts
