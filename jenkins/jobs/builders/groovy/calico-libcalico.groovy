def tools = new ci.mcp.Tools()

node('calico'){

  // Get Artifactory server instance, defined in the Artifactory Plugin administration page.
  // TODO(skulanov) use sandbox
  //def server = Artifactory.server("mcp-ci")
  def server = Artifactory.newServer url: "https://artifactory.mcp.mirantis.net/artifactory", username: "sandbox", password: "sandbox"

  def DOCKER_REPO = "artifactory.mcp.mirantis.net:5004"
  def ARTIFACTORY_URL = "https://artifactory.mcp.mirantis.net/artifactory/sandbox"
  def ARTIFACTORY_USER_EMAIL = "jenkins@mcp-ci-artifactory"
  def GERRIT_HOST = "review.fuel-infra.org"

  def NODE_IMAGE = "calico/node"
  def NODE_IMAGE_TAG = "v0.20.0"
  def NODE_NAME = "${DOCKER_REPO}/${NODE_IMAGE}:${NODE_IMAGE_TAG}"

  def CTL_IMAGE = "calico/ctl"
  def CTL_IMAGE_TAG = "v0.20.0"
  def CTL_NAME = "${DOCKER_REPO}/${CTL_IMAGE}:${CTL_IMAGE_TAG}"

  def BUILD_IMAGE = "calico/build"
  def BUILD_IMAGE_TAG = "v0.15.0"

  try {

    gitSSHCheckout {
      credentialsId = "mcp-ci-gerrit"
      branch = "mcp"
      host = "review.fuel-infra.org"
      project = "projectcalico/calico-containers"
    }

    dir("${WORKSPACE}/calico_node/calico_share"){
      sh "rm -rf .gitkeep"

      gerritPatchsetCheckout {
        credentialsId = "mcp-ci-gerrit"
        withMerge = true
        withWipeOut = true
      }

      def gitCommit = sh(returnStdout: true, script: "git -C ${WORKSPACE}/calico_node/calico_share rev-parse --short HEAD").trim()
      // LIBCALICO_DOCKER_IMAGE defined here globaly, since we depends on git-sha
      LIBCALICO_DOCKER_IMAGE="${DOCKER_REPO}/${BUILD_IMAGE}:${BUILD_IMAGE_TAG}-${GERRIT_CHANGE_NUMBER}-${gitCommit}"


      stage ('Run libcalico unittest') {
        sh "make test"
      }


      stage ('Build libcalico image') {
        sh "docker build -t ${LIBCALICO_DOCKER_IMAGE} ."
      }


      stage ('Publishing libcalico artifacts') {

        withCredentials([
          [$class: 'UsernamePasswordMultiBinding',
            credentialsId: 'artifactory-sandbox',
            passwordVariable: 'ARTIFACTORY_PASSWORD',
            usernameVariable: 'ARTIFACTORY_LOGIN']
        ]) {
          sh """
            echo 'Pushing images'
            docker login -u ${ARTIFACTORY_LOGIN} -p ${ARTIFACTORY_PASSWORD} ${DOCKER_REPO}
            docker push ${LIBCALICO_DOCKER_IMAGE}
          """
        }

        dir("artifacts"){

          // Save Image name ID
          writeFile file: "lastbuild", text: "${LIBCALICO_DOCKER_IMAGE}"
          // Create the upload spec.
          def uploadSpec = """{
              "files": [
                      {
                          "pattern": "**",
                          "target": "sandbox/${GERRIT_CHANGE_NUMBER}/libcalico/"
                      }
                  ]
              }"""

          // Upload to Artifactory.
          def buildInfo1 = server.upload(uploadSpec)
        } // dir artifacts

      } // stage

    } // build libcalico


    stage ('Building calico-cni') {

      dir("calico-cni"){
        gitSSHCheckout {
          credentialsId = "mcp-ci-gerrit"
          branch = "mcp"
          host = "review.fuel-infra.org"
          project = "projectcalico/calico-cni"
        }

        def gitCommit = sh(returnStdout: true, script: "git -C ${WORKSPACE}/calico-cni rev-parse --short HEAD").trim()
        def CNI_BUILD = "${GERRIT_CHANGE_NUMBER}-${gitCommit}"

        sh """
          docker run --rm \
            -v ${WORKSPACE}/calico-cni:/code ${LIBCALICO_DOCKER_IMAGE} \
            sh -c \
            \"pip install pykube && pyinstaller calico.py -ayF && pyinstaller ipam.py -ayF -n calico-ipam\"
        """

        stage('Publishing calico-cni artifacts'){
          dir("artifacts"){

            sh """
              cp ${WORKSPACE}/calico-cni/dist/calico calico-${CNI_BUILD}
              cp ${WORKSPACE}/calico-cni/dist/calico-ipam calico-ipam-${CNI_BUILD}
            """
            // Save Image name ID
            writeFile file: "lastbuild", text: "${CNI_BUILD}"
            // Create the upload spec.
            writeFile file: "calico-cni-${CNI_BUILD}.yaml",
                      text: """\
                        calico_cni_download_url: ${ARTIFACTORY_URL}/${GERRIT_CHANGE_NUMBER}/calico-cni/calico-${CNI_BUILD}
                        calicoctl_image_repo: ${DOCKER_REPO}/${CTL_IMAGE}
                        calico_version: ${NODE_IMAGE_TAG}-${CNI_BUILD}
                      """.stripIndent()
            def uploadSpec = """{
                "files": [
                        {
                            "pattern": "**",
                            "target": "sandbox/${GERRIT_CHANGE_NUMBER}/calico-cni/"
                        }
                    ]
                }"""

            // Upload to Artifactory.
            def buildInfo1 = server.upload(uploadSpec)
          }
        } // publishing artifacts
      } // dir calico-cni
    } // stage build calico


    stage ('Start building calico-containers') {
      // Fake stage to show that we switched to calico-containers
      echo "Start building calico-containers"
    }

    def CALICO_REPO = "https://${GERRIT_HOST}/projectcalico/calico"
    def CALICO_VER = "mcp"
    def LIBCALICO_REPO = "file:///tmp/calico_share"
    def LIBCALICO_VER = "mcp"

    def CONFD_BUILD = "${ARTIFACTORY_URL}/mcp/confd/lastbuild".toURL().text.trim()
    def CONFD_URL = "${ARTIFACTORY_URL}/mcp/confd/confd-${CONFD_BUILD}"

    def BIRD_BUILD="${ARTIFACTORY_URL}/mcp/calico-bird/lastbuild".toURL().text.trim()
    def BIRD_URL="${ARTIFACTORY_URL}/mcp/calico-bird/bird-${BIRD_BUILD}"
    def BIRD6_URL="${ARTIFACTORY_URL}/mcp/calico-bird/bird6-${BIRD_BUILD}"
    def BIRDCL_URL="${ARTIFACTORY_URL}/mcp/calico-bird/birdcl-${BIRD_BUILD}"

    gitCommit = sh(returnStdout: true, script: "git -C ${WORKSPACE} rev-parse --short HEAD").trim()

    def BUILD = "${GERRIT_CHANGE_NUMBER}-${gitCommit}"


    stage ('Build calico/ctl image'){
      sh """
        make ctl_image \
          REBUILD_CALICOCTL=1 \
          CTL_CONTAINER_NAME=${CTL_NAME}-${BUILD} \
          BUILD_CONTAINER_NAME=${LIBCALICO_DOCKER_IMAGE} \
          BIRDCL_URL=${BIRDCL_URL}
      """
    }


    stage('Build calico/node'){
      sh """
        make node_image \
          NODE_CONTAINER_NAME=${NODE_NAME}-${BUILD} \
          BUILD_CONTAINER_NAME=${LIBCALICO_DOCKER_IMAGE} \
          CONFD_URL=${CONFD_URL} \
          BIRD_URL=${BIRD_URL} \
          BIRD6_URL=${BIRD6_URL} \
          BIRDCL_URL=${BIRDCL_URL}
      """
    }


    stage('Publishing containers artifacts'){

      withCredentials([
        [$class: 'UsernamePasswordMultiBinding',
          credentialsId: 'artifactory-sandbox',
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
                        "target": "sandbox/${GERRIT_CHANGE_NUMBER}/calico-containers/"
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
