node('calico'){

  // Get Artifactory server instance, defined in the Artifactory Plugin administration page.
  def server = Artifactory.server("mcp-ci")

  try {

    gitSSHCheckout {
      credentialsId = "mcp-ci-gerrit"
      branch = "mcp"
      host = "review.fuel-infra.org"
      project = "projectcalico/calico-containers"
    }

    dir("${WORKSPACE}/confd_repo"){

      def container_src_dir = "/usr/src/confd"
      def src_suffix = "src/github.com/kelseyhightower/confd"
      def container_workdir = "${container_src_dir}/${src_suffix}"
      def container_gopath = "${container_src_dir}/vendor:${container_src_dir}"

      gitSSHCheckout {
        credentialsId = "mcp-ci-gerrit"
        branch = "mcp"
        host = "review.fuel-infra.org"
        project = "projectcalico/confd"
      }


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

          def gitCommit = sh(returnStdout: true, script: "git -C ${WORKSPACE}/confd_repo rev-parse --short HEAD").trim()
          def CONFD_BUILD_ID = "mcp-${gitCommit}"
          // Save the last build ID
          writeFile file: "lastbuild", text: "${CONFD_BUILD_ID}"
          sh "cp ${WORKSPACE}/confd_repo/${src_suffix}/confd confd-${CONFD_BUILD_ID}"

          // Create the upload spec.
          def uploadSpec = """{
              "files": [
                      {
                          "pattern": "**",
                          "target": "projectcalico/mcp/confd/"
                      }
                  ]
              }"""

          // Upload to Artifactory.
          def buildInfo1 = server.upload(uploadSpec)
        } // dir
      }
    }// dir


    stage ('Start building calico-containers') {
      // Fake stage to show that we switched to calico-containers
      echo "Start building calico-containers"
    }

    def DOCKER_REPO = "artifactory.mcp.mirantis.net:5001"
    def ARTIFACTORY_URL = "https://artifactory.mcp.mirantis.net/artifactory/projectcalico"

    def NODE_IMAGE = "calico/node"
    def NODE_IMAGE_TAG = "v0.20.0"
    def NODE_NAME = "${DOCKER_REPO}/${NODE_IMAGE}:${NODE_IMAGE_TAG}"

    def CTL_IMAGE = "calico/ctl"
    def CTL_IMAGE_TAG = "v0.20.0"
    def CTL_NAME = "${DOCKER_REPO}/${CTL_IMAGE}:${CTL_IMAGE_TAG}"

    // calico/build goes from {ARTIFACTORY_URL}/mcp/libcalico/
    def BUILD_NAME = "${ARTIFACTORY_URL}/mcp/libcalico/lastbuild".toURL().text.trim()
    // calico/felix goes from {ARTIFACTORY_URL}/mcp/felix/
    def FELIX_NAME = "${ARTIFACTORY_URL}/mcp/felix/lastbuild".toURL().text.trim()

    def CONFD_BUILD = "${ARTIFACTORY_URL}/mcp/confd/lastbuild".toURL().text.trim()
    def CONFD_URL = "${ARTIFACTORY_URL}/mcp/confd/confd-${CONFD_BUILD}"

    def BIRD_BUILD="${ARTIFACTORY_URL}/mcp/calico-bird/lastbuild".toURL().text.trim()
    def BIRD_URL="${ARTIFACTORY_URL}/mcp/calico-bird/bird-${BIRD_BUILD}"
    def BIRD6_URL="${ARTIFACTORY_URL}/mcp/calico-bird/bird6-${BIRD_BUILD}"
    def BIRDCL_URL="${ARTIFACTORY_URL}/mcp/calico-bird/birdcl-${BIRD_BUILD}"

    def gitCommit = sh(returnStdout: true, script: "git -C ${WORKSPACE} rev-parse --short HEAD").trim()

    def BUILD = "mcp-${gitCommit}"


    stage ('Build calico/ctl image'){
      sh """
        make ctl_image \
          REBUILD_CALICOCTL=1 \
          CTL_CONTAINER_NAME=${CTL_NAME}-${BUILD} \
          BUILD_CONTAINER_NAME=${BUILD_NAME} \
          BIRDCL_URL=${BIRDCL_URL}
      """
    }


    stage('Build calico/node'){
      sh """
        make node_image \
          NODE_CONTAINER_NAME=${NODE_NAME}-${BUILD} \
          BUILD_CONTAINER_NAME=${BUILD_NAME} \
          FELIX_CONTAINER_NAME=${FELIX_NAME} \
          CONFD_URL=${CONFD_URL} \
          BIRD_URL=${BIRD_URL} \
          BIRD6_URL=${BIRD6_URL} \
          BIRDCL_URL=${BIRDCL_URL}
      """
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
          docker login -u ${ARTIFACTORY_LOGIN} -p ${ARTIFACTORY_PASSWORD} ${DOCKER_REPO}
          docker push ${NODE_NAME}-${BUILD}
          docker push ${CTL_NAME}-${BUILD}
        """
      }

      dir("artifacts"){
        // Save the last build ID
        writeFile file: "lastbuild", text: "${BUILD}"
        // Create config yaml for Kargo
        writeFile file: "calico-containers-${BUILD}.yaml",
                  text: """\
                    calico_node_image_repo: ${DOCKER_REPO}/${NODE_IMAGE}
                    calicoctl_image_repo: ${DOCKER_REPO}/${CTL_IMAGE}
                    calico_version: ${NODE_IMAGE_TAG}-${BUILD}
                  """.stripIndent()
        // Create the upload spec.
        def uploadSpec = """{
            "files": [
                    {
                        "pattern": "**",
                        "target": "projectcalico/mcp/calico-containers/"
                    }
                ]
            }"""

        // Upload to Artifactory.
        def buildInfo1 = server.upload(uploadSpec)
      } // dir artifacts
    } //stage

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
