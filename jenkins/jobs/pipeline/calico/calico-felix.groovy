node('calico'){

  try {

    def tools = new ci.mcp.Tools()

    def server = Artifactory.server("mcp-ci")
    def dockerRepository = env.DOCKER_REGISTRY

    def buildInfo = Artifactory.newBuildInfo()
    buildInfo.env.capture = true
    buildInfo.env.filter.addInclude("*")
    buildInfo.env.collect()

    def currentBuildId = ""
    def compareBranch = ""
    // this name goes to calico-containers
    def felixName = ""

    def HOST = env.GERRIT_HOST
    gitSSHCheckout {
      credentialsId = "mcp-ci-gerrit"
      branch = "mcp"
      host = HOST
      project = "projectcalico/calico-containers"
    }

    dir("${env.WORKSPACE}/tmp_calico-felix"){
      // Let's do all the stuff with calico/felix in tmp_calico-felix sub-dir
      def felixImg = "calico/felix"
      def felixImgTag = "dev"

      if ( env.GERRIT_EVENT_TYPE == 'patchset-created' ) {
        currentBuildId = env.GERRIT_CHANGE_NUMBER
        compareBranch = "gerrit/${env.GERRIT_BRANCH}"

        stage ('Checkout felix'){
          gerritPatchsetCheckout {
            credentialsId = "mcp-ci-gerrit"
            withWipeOut = true
          }
        }
      } else {
        currentBuildId = "mcp"
        compareBranch = "origin/mcp"

        stage ('Checkout felix'){
          gitSSHCheckout {
            credentialsId = "mcp-ci-gerrit"
            branch = "mcp"
            host = HOST
            project = "projectcalico/felix"
          }
        }
      } // else

      def gitCommit = tools.getGitCommit().take(7)

      felixName="${dockerRepository}/${felixImg}:${felixImgTag}-${currentBuildId}-${gitCommit}"

      stage ('Run felix unittests'){
        // inject COMPARE_BRANCH variable for felix coverage test
        sh "make ut UT_COMPARE_BRANCH=${compareBranch}"
      }

      // GO binary is built as dependency for Docker image,
      // there is no need to build it in a separate stage
      stage ('Build calico/felix image') {
        sh """
              make calico/felix
              docker tag calico/felix ${felixName}
           """
      }


      stage ('Publishing felix artifacts') {

        withCredentials([
          [$class: 'UsernamePasswordMultiBinding',
            credentialsId: 'artifactory',
            passwordVariable: 'ARTIFACTORY_PASSWORD',
            usernameVariable: 'ARTIFACTORY_LOGIN']
        ]) {
          sh """
            echo 'Pushing images'
            docker login -u ${ARTIFACTORY_LOGIN} -p ${ARTIFACTORY_PASSWORD} ${dockerRepository}
            docker push ${felixName}
          """
        }

        dir("artifacts"){

          def properties = tools.getBinaryBuildProperties()

          // Save Image name ID
          writeFile file: "lastbuild", text: "${felixName}"
          // Create the upload spec.
          def uploadSpec = """{
              "files": [
                      {
                          "pattern": "**",
                          "target": "projectcalico/${currentBuildId}/felix/",
                          "props": "${properties}"
                      }
                  ]
              }"""

          // Upload to Artifactory.
          server.upload(uploadSpec, buildInfo)
        } // dir artifacts

      } // stage


    }// dir

    // start building calico-containers
    def calicoContainersArts = buildCalicoContainers {
      containersBuildId = currentBuildId
      felixImage = felixName
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
    sh "sudo chown -R jenkins:jenkins ${env.WORKSPACE} ${env.HOME}/.glide"
  }
}
