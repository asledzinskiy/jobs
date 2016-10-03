def tools = new ci.mcp.Tools()

node('calico'){

  // Get Artifactory server instance, defined in the Artifactory Plugin administration page.
  // TODO(skulanov) use sandbox
  //def server = Artifactory.server("mcp-ci")
  def server = Artifactory.newServer url: "https://artifactory.mcp.mirantis.net/artifactory", username: "sandbox", password: "sandbox"

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


      stage ('Run Calico unittests'){
        parallel (

          UnitTest: {
            sh '''#!/bin/bash

                  set -ex

                  prepare_pyenv () {
                      PACKAGES=("tox" "coverage")
                      VENV_PATH="${WORKSPACE}/python-venv-${JOB_NAME}-${BUILD_NUMBER}"
                      virtualenv "${VENV_PATH}"
                      source "${VENV_PATH}/bin/activate"
                      pip install ${PACKAGES[@]}
                  }

                  clean_pyenv () {
                    if [ -d "$VIRTUAL_ENV" ]; then
                        VENV_PATH="$VIRTUAL_ENV"
                        deactivate
                        rm -rf "${VENV_PATH}"
                    fi
                  }

                  prepare_pyenv

                  if bash -x -c 'VIRTUAL_ENV="" ./run-unit-test.sh'; then
                      clean_pyenv
                      echo "Tests passed!"
                  else
                      clean_pyenv
                      echo "Tests failed!"
                      exit 1
                  fi
            '''
          },

          PEP8Test: {
            sh '''#!/bin/bash -ex
                  if tox -l | grep -qw pep8; then
                      tox -v -e pep8
                  fi
            '''
          },

          failFast: true
        ) //parallel
      } // stage
    }// dir


    stage ('Start building calico-containers') {
      // Fake stage to show that we switched to calico-containers
      echo "Start building calico-containers"
    }

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

    def CALICO_REPO = "file:///tmp/calico_share"
    def CALICO_VER = "mcp"
    def LIBCALICO_REPO = "https://${GERRIT_HOST}/projectcalico/libcalico"
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
          BUILD_CONTAINER_NAME=${BUILD_IMAGE}:${BUILD_IMAGE_TAG} \
          BIRDCL_URL=${BIRDCL_URL}
      """
    }


    stage('Build calico/node'){
      sh """
        make node_image \
          NODE_CONTAINER_NAME=${NODE_NAME}-${BUILD} \
          BUILD_CONTAINER_NAME=${BUILD_IMAGE}:${BUILD_IMAGE_TAG} \
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
