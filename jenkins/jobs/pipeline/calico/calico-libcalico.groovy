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
    // this name goes to calico-containers
    def buildName = ""

    def HOST = env.GERRIT_HOST
    gitSSHCheckout {
      credentialsId = "mcp-ci-gerrit"
      branch = "mcp"
      host = HOST
      project = "projectcalico/calico-containers"
    }

    dir("${WORKSPACE}/tmp_libcalico"){

      // Let's do all the stuff with calico/felix in tmp_calico-felix sub-dir
      def buildImg = "calico/build"
      def buildImgTag = "v0.15.0"

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
            project = "projectcalico/libcalico"
          }
        }
      } // else

      def gitCommit = tools.getGitCommit().take(7)
      // LIBCALICO_DOCKER_IMAGE defined here globaly, since we depends on git-sha
      buildName="${dockerRepository}/${buildImg}:${buildImgTag}-${currentBuildId}-${gitCommit}"


      stage ('Run libcalico unittest') {
        sh "make test"
      }


      stage ('Build libcalico image') {
        sh "docker build -t ${buildName} ."
      }


      stage ('Publishing libcalico artifacts') {

        withCredentials([
          [$class: 'UsernamePasswordMultiBinding',
            credentialsId: 'artifactory',
            passwordVariable: 'ARTIFACTORY_PASSWORD',
            usernameVariable: 'ARTIFACTORY_LOGIN']
        ]) {
          sh """
            echo 'Pushing images'
            docker login -u ${ARTIFACTORY_LOGIN} -p ${ARTIFACTORY_PASSWORD} ${dockerRepository}
            docker push ${buildName}
          """
        }

        dir("artifacts"){

          def properties = tools.getBinaryBuildProperties()
          // Save Image name ID
          writeFile file: "lastbuild", text: "${buildName}"
          // Create the upload spec.
          def uploadSpec = """{
              "files": [
                      {
                          "pattern": "**",
                          "target": "projectcalico/${currentBuildId}/libcalico/",
                          "props": "${properties}"
                      }
                  ]
              }"""

          // Upload to Artifactory.
          server.upload(uploadSpec, buildInfo)
        } // dir artifacts

      } // stage

    } // build libcalico

    // start building calico-containers
    def calicoContainersArts = buildCalicoContainers {
      containersBuildId = currentBuildId
      buildImage = buildName
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
