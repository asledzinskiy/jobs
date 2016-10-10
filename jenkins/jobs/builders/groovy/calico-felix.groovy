def tools = new ci.mcp.Tools()

node('calico'){

  // Get Artifactory server instance, defined in the Artifactory Plugin administration page.
  // TODO(skulanov) use sandbox
  //def server = Artifactory.server("mcp-ci")
  def server = Artifactory.newServer url: "https://artifactory.mcp.mirantis.net/artifactory", username: "sandbox", password: "sandbox"
  def DOCKER_REPO = "artifactory.mcp.mirantis.net:5004"
  def ARTIFACTORY_URL = "https://artifactory.mcp.mirantis.net/artifactory/sandbox"

  def FELIX_IMAGE = "calico/felix"
  def FELIX_IMAGE_TAG = "dev"

  try {

    gitSSHCheckout {
      credentialsId = "mcp-ci-gerrit"
      branch = "mcp"
      host = "review.fuel-infra.org"
      project = "projectcalico/calico-containers"
    }

    dir("${WORKSPACE}/tmp_calico-felix"){
      // Let's do all the stuff with calico/felix in tmp_calico-felix sub-dir

      // checkout felix code from patchset
      gerritPatchsetCheckout {
        credentialsId = "mcp-ci-gerrit"
        withWipeOut = true
      }

      // get felix git-sha
      def gitCommit = sh(returnStdout: true, script: "git -C ${WORKSPACE}/tmp_calico-felix rev-parse --short HEAD").trim()
      // FELIX_DOCKER_IMAGE defined here globaly, since we depends on git-sha and need to pass this
      // variable to the next stages
      FELIX_DOCKER_IMAGE="${DOCKER_REPO}/${FELIX_IMAGE}:${FELIX_IMAGE_TAG}-${GERRIT_CHANGE_NUMBER}-${gitCommit}"

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

                  if bash -x -c \'COMPARE_BRANCH=gerrit/${GERRIT_BRANCH} VIRTUAL_ENV="" ./run-unit-test.sh\'; then
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

      // TODO(skulanov): the below should be used if we need to customize building container name
      // stage ('Build calico-pyi-build image') {
      //   sh "docker build -t calico-pyi-build -f pyi/Dockerfile ."
      // }

      // // use build image from prev stage to build binary
      // stage ('Build calico-felix binary') {
      //   sh "docker run --user `id -u` --rm -v ${WORKSPACE}/tmp_calico-felix:/code calico-pyi-build /code/pyi/run-pyinstaller.sh"
      // }
      stage ('Build calico-felix binary'){
        sh "./build-pyi-bundle.sh"
      }

      // build calico/felix image which consumes bin from prev step
      stage ('Build calico/felix image') {
        sh "docker build -t ${FELIX_DOCKER_IMAGE} ."
      }


      stage ('Publishing felix artifacts') {

        withCredentials([
          [$class: 'UsernamePasswordMultiBinding',
            credentialsId: 'artifactory-sandbox',
            passwordVariable: 'ARTIFACTORY_PASSWORD',
            usernameVariable: 'ARTIFACTORY_LOGIN']
        ]) {
          sh """
            echo 'Pushing images'
            docker login -u ${ARTIFACTORY_LOGIN} -p ${ARTIFACTORY_PASSWORD} ${DOCKER_REPO}
            docker push ${FELIX_DOCKER_IMAGE}
          """
        }

        dir("artifacts"){

          // Save Image name ID
          writeFile file: "lastbuild", text: "${FELIX_DOCKER_IMAGE}"
          // Create the upload spec.
          def uploadSpec = """{
              "files": [
                      {
                          "pattern": "**",
                          "target": "sandbox/${GERRIT_CHANGE_NUMBER}/felix/"
                      }
                  ]
              }"""

          // Upload to Artifactory.
          def buildInfo1 = server.upload(uploadSpec)
        } // dir artifacts

      } // stage


    }// dir


    stage ('Start building calico-containers') {
      // Fake stage to show that we switched to calico-containers
      echo "Start building calico-containers"
    }

    def NODE_IMAGE = "calico/node"
    def NODE_IMAGE_TAG = "v0.20.0"
    def NODE_NAME = "${DOCKER_REPO}/${NODE_IMAGE}:${NODE_IMAGE_TAG}"

    def CTL_IMAGE = "calico/ctl"
    def CTL_IMAGE_TAG = "v0.20.0"
    def CTL_NAME = "${DOCKER_REPO}/${CTL_IMAGE}:${CTL_IMAGE_TAG}"

    // calico/build goes from {ARTIFACTORY_URL}/mcp/libcalico/
    def BUILD_NAME = "${ARTIFACTORY_URL}/mcp/libcalico/lastbuild".toURL().text.trim()

    def CONFD_BUILD = "${ARTIFACTORY_URL}/mcp/confd/lastbuild".toURL().text.trim()
    def CONFD_URL = "${ARTIFACTORY_URL}/mcp/confd/confd-${CONFD_BUILD}"

    def BIRD_BUILD="${ARTIFACTORY_URL}/mcp/calico-bird/lastbuild".toURL().text.trim()
    def BIRD_URL="${ARTIFACTORY_URL}/mcp/calico-bird/bird-${BIRD_BUILD}"
    def BIRD6_URL="${ARTIFACTORY_URL}/mcp/calico-bird/bird6-${BIRD_BUILD}"
    def BIRDCL_URL="${ARTIFACTORY_URL}/mcp/calico-bird/birdcl-${BIRD_BUILD}"

    def gitCommit = sh(returnStdout: true, script: "git -C ${WORKSPACE} rev-parse --short HEAD").trim()

    def BUILD = "${GERRIT_CHANGE_NUMBER}-${gitCommit}"


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
          FELIX_CONTAINER_NAME=${FELIX_DOCKER_IMAGE} \
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
