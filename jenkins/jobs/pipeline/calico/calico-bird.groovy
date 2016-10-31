node('calico'){

  try {

    def tools = new ci.mcp.Tools()

    def server = Artifactory.server("mcp-ci")
    def dockerRepository = "artifactory.mcp.mirantis.net:5001"

    def buildInfo = Artifactory.newBuildInfo()
    buildInfo.env.capture = true
    buildInfo.env.filter.addInclude("*")
    buildInfo.env.collect()

    def currentBuildId = ""

    def HOST = env.GERRIT_HOST
    gitSSHCheckout {
      credentialsId = "mcp-ci-gerrit"
      branch = "mcp"
      host = HOST
      project = "projectcalico/calico-containers"
    }

    // we need to use downstream libcalico, so let's check it out
    gitSSHCheckout {
      credentialsId = "mcp-ci-gerrit"
      branch = "mcp"
      host = HOST
      withMerge = true
      project = "projectcalico/libcalico"
      targetDir = "calico_node/node_share/libcalico"
    }

    dir("${WORKSPACE}/bird_repo"){

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
            project = "projectcalico/calico-bird"
          }
        }
      } // else

      stage ('Build bird binaries'){
        sh "/bin/sh -x build.sh"
      }

      stage('Publishing bird artifacts'){
        dir("artifacts"){

          def gitCommit = tools.getGitCommit().take(7)
          def BUILD_ID = "${currentBuildId}-${gitCommit}"
          // Save the last build ID
          writeFile file: "lastbuild", text: "${BUILD_ID}"

          sh """
            cp ${WORKSPACE}/bird_repo/dist/bird bird-${BUILD_ID}
            cp ${WORKSPACE}/bird_repo/dist/bird6 bird6-${BUILD_ID}
            cp ${WORKSPACE}/bird_repo/dist/birdcl birdcl-${BUILD_ID}
          """

          def properties = tools.getBinaryBuildProperties()

          // Create the upload spec.
          def uploadSpec = """{
              "files": [
                      {
                          "pattern": "**",
                          "target": "projectcalico/${currentBuildId}/calico-bird/",
                          "props": "${properties}"
                      }
                  ]
              }"""

          // Upload to Artifactory.
          server.upload(uploadSpec, buildInfo)
        } // dir artifacts
      } // stage publishing

    }// dir

    def artifactoryUrl = "https://artifactory.mcp.mirantis.net/artifactory/projectcalico"
    def birdBuildId = "${artifactoryUrl}/${currentBuildId}/calico-bird/lastbuild".toURL().text.trim()

    // start building calico-containers
    def calicoContainersArts = buildCalicoContainers {
      artifactoryURL = artifactoryUrl
      dockerRepo = dockerRepository
      containersBuildId = currentBuildId
      birdUrl = "${artifactoryUrl}/${currentBuildId}/calico-bird/bird-${birdBuildId}"
      bird6Url = "${artifactoryUrl}/${currentBuildId}/calico-bird/bird6-${birdBuildId}"
      birdclUrl = "${artifactoryUrl}/${currentBuildId}/calico-bird/birdcl-${birdBuildId}"
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
      } // dir artifacts
    } //stage

    // publish buildInfo
    server.publishBuildInfo buildInfo

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
  catch(err) {
    echo "Failed: ${err}"
    currentBuild.result = 'FAILURE'
  }
  finally {
    // fix workspace owners
    sh "sudo chown -R jenkins:jenkins ${WORKSPACE}"
  }

}
