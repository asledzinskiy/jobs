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

    dir("${WORKSPACE}/confd_repo"){

      def container_src_dir = "/usr/src/confd"
      def src_suffix = "src/github.com/kelseyhightower/confd"
      def container_workdir = "${container_src_dir}/${src_suffix}"
      def container_gopath = "${container_src_dir}/vendor:${container_src_dir}"

      if ( env.GERRIT_EVENT_TYPE == 'patchset-created' ) {
        currentBuildId = env.GERRIT_CHANGE_NUMBER

        stage ('Checkout confd'){
          gerritPatchsetCheckout {
            credentialsId = "mcp-ci-gerrit"
            withWipeOut = true
          }
        }
      } else {
        currentBuildId = "mcp"

        stage ('Checkout confd'){
          gitSSHCheckout {
            credentialsId = "mcp-ci-gerrit"
            branch = "mcp"
            host = HOST
            project = "projectcalico/confd"
          }
        }
      } // else


      stage ('Run UnitTest for confd'){
        sh """
        docker run --rm \
          -v ${WORKSPACE}/confd_repo:/usr/src/confd \
          -w /usr/src/confd \
          golang:1.7 \
          bash -c \
          \"go get github.com/constabulary/gb/...; gb test -v\"
        """
      }


      stage ('Build confd binary'){
        sh """
          docker run --rm \
            -v ${WORKSPACE}/confd_repo:${container_src_dir} \
            -w ${container_workdir} \
            -e GOPATH=${container_gopath} \
            golang:1.7 \
            bash -c \
            \"go build -a -installsuffix cgo -ldflags '-extld ld -extldflags -static' -a -x .\"
        """
      }


      stage('Publishing confd artifacts'){
        dir("artifacts"){

          def gitCommit = tools.getGitCommit().take(7)
          def CONFD_BUILD_ID = "${currentBuildId}-${gitCommit}"
          // Save the last build ID
          writeFile file: "lastbuild", text: "${CONFD_BUILD_ID}"
          sh "cp ${WORKSPACE}/confd_repo/${src_suffix}/confd confd-${CONFD_BUILD_ID}"

          def properties = tools.getBinaryBuildProperties()

          // Create the upload spec.
          def uploadSpec = """{
              "files": [
                      {
                          "pattern": "**",
                          "target": "projectcalico/${currentBuildId}/confd/",
                          "props": "${properties}"
                      }
                  ]
              }"""
          // Upload to Artifactory.
          server.upload(uploadSpec, buildInfo)
        } // dir
      }
    }// dir


    def artifactoryUrl = "https://artifactory.mcp.mirantis.net/artifactory/projectcalico"
    def confdBuildId = "${artifactoryUrl}/${currentBuildId}/confd/lastbuild".toURL().text.trim()

    // start building calico-containers
    def calicoContainersArts = buildCalicoContainers {
      artifactoryURL = artifactoryUrl
      dockerRepo = dockerRepository
      containersBuildId = currentBuildId
      confdUrl = "${artifactoryUrl}/${currentBuildId}/confd/confd-${confdBuildId}"
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
    sh "sudo chown -R jenkins:jenkins ${WORKSPACE}"
  }
}
