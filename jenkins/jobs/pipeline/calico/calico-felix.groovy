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

    dir("${WORKSPACE}/tmp_calico-felix"){
      // Let's do all the stuff with calico/felix in tmp_calico-felix sub-dir
      def felixImg = "calico/felix"
      def felixImgTag = "dev"

      if ( env.GERRIT_EVENT_TYPE == 'patchset-created' ) {
        currentBuildId = env.GERRIT_CHANGE_NUMBER
        compareBranch = "gerrit/${env.GERRIT_BRANCH}"

        stage ('Checkout calico-containers'){
          gerritPatchsetCheckout {
            credentialsId = "mcp-ci-gerrit"
            withWipeOut = true
          }
        }
      } else {
        currentBuildId = "mcp"
        compareBranch = "origin/mcp"

        stage ('Checkout calico-containers'){
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
        parallel (

          UnitTest: {
            // inject COMPARE_BRANCH variable for felix coverage test
            env.COMPARE_BRANCH = compareBranch
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

                  if bash -x -c \'VIRTUAL_ENV="" ./run-unit-test.sh\'; then
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
        sh "docker build -t ${felixName} ."
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
