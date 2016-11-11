node('calico'){

  try {

    def tools = new ci.mcp.Tools()

    def server = Artifactory.server("mcp-ci")
    def dockerRepository = env.DOCKER_REGISTRY

    def currentBuildId = ""

    def buildInfo = Artifactory.newBuildInfo()
    buildInfo.env.capture = true
    buildInfo.env.filter.addInclude("*")
    buildInfo.env.collect()

    def HOST = env.GERRIT_HOST

    if ( env.GERRIT_EVENT_TYPE == 'patchset-created' ) {
      currentBuildId = env.GERRIT_CHANGE_NUMBER

      stage ('Checkout calico-containers'){
        gerritPatchsetCheckout {
          credentialsId = "mcp-ci-gerrit"
          withWipeOut = true
        }
      }
    } else {
      currentBuildId = "mcp"

      stage ('Checkout calico-containers'){
        gitSSHCheckout {
          credentialsId = "mcp-ci-gerrit"
          branch = "mcp"
          host = HOST
          project = "projectcalico/calico-containers"
        }
      }
    }

    // Run unit tests
    stage ('Run unittest') { sh "make test-containerized"  }

    // build calico-containers
    def calicoContainersArts = buildCalicoContainers {
      containersBuildId = currentBuildId
    }

    stage('Publishing containers artifacts'){

      withCredentials([
        [$class: 'UsernamePasswordMultiBinding',
          credentialsId: 'artifactory',
          passwordVariable: 'ARTIFACTORY_PASSWORD',
          usernameVariable: 'ARTIFACTORY_LOGIN']
      ]) {
        sh """
          echo 'Pushing images'
          docker login -u ${ARTIFACTORY_LOGIN} -p ${ARTIFACTORY_PASSWORD} ${dockerRepository}
          docker push ${calicoContainersArts["CTL_CONTAINER_NAME"]}
          docker push ${calicoContainersArts["NODE_CONTAINER_NAME"]}
        """
      }

      dir("artifacts"){
        def properties = tools.getBinaryBuildProperties()
        // Create the upload spec.
        def uploadSpec = """{
            "files": [
                    {
                        "pattern": "**",
                        "target": "projectcalico/${currentBuildId}/calico-containers/",
                        "props": "${properties}"
                    }
                ]
            }"""
        // Upload to Artifactory.
        server.upload(uploadSpec, buildInfo)
        server.publishBuildInfo buildInfo
      } // dir artifacts
    } //stage


    if ( env.GERRIT_EVENT_TYPE == 'patchset-created' ) {
      stage ("Run system tests") {
         build job: 'calico.system-test.deploy', propagate: true, wait: true, parameters:
          [
              [$class: 'StringParameterValue', name: 'CALICO_NODE_IMAGE_REPO', value: calicoContainersArts["CALICO_NODE_IMAGE_REPO"]],
              [$class: 'StringParameterValue', name: 'CALICOCTL_IMAGE_REPO', value: calicoContainersArts["CALICOCTL_IMAGE_REPO"]],
              [$class: 'StringParameterValue', name: 'CALICO_VERSION', value: calicoContainersArts["CALICO_VERSION"]],
              [$class: 'StringParameterValue', name: 'MCP_BRANCH', value: 'mcp'],
          ]
      }
    }

  }
  catch(err) {
    echo "Failed: ${err}"
    currentBuild.result = 'FAILURE'
  }
  finally {
    // fix workspace owners
    sh "sudo chown -R jenkins:jenkins ${WORKSPACE}"
  }
}
