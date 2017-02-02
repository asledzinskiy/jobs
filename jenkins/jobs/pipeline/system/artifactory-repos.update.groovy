def ciTools = new com.mirantis.mcp.Common()
def gitTools = new com.mirantis.mcp.Git()
def server = Artifactory.server('mcp-ci')
def artifactoryUrl = server.getUrl()
def gerritHost = env.GERRIT_HOST

node('tools') {

    stage('Project-config code checkout') {
      if (env.GERRIT_EVENT_TYPE) {
        gitTools.gerritPatchsetCheckout([
          credentialsId: "mcp-ci-gerrit"
        ])
      } else {
        gitTools.gitSSHCheckout ([
          credentialsId: "mcp-ci-gerrit",
          branch: "master",
          host: gerritHost,
          project: "mcp-ci/project-config"
        ])
      }
    }

    stage('Jimbo code checkout') {
      gitTools.gitSSHCheckout ([
        credentialsId: "mcp-ci-gerrit",
        branch: "master",
        host: gerritHost,
        project: "mcp-ci/jimbo",
        targetDir: "${env.WORKSPACE}/jimbo"
      ])
    }

    withEnv(["VENV_PATH=${env.WORKSPACE}/.tox/artifactory-repos-update",
             "ARTIFACTORY_URL=${artifactoryUrl}"]) {
        stage('Prepare virtual env') {
          sh '''
            virtualenv ${VENV_PATH}
            bash -c "source ${VENV_PATH}/bin/activate && pip install ${WORKSPACE}/jimbo/"
          '''
        }

        stage('Update repositories') {
          withCredentials([
                  [$class: 'UsernamePasswordMultiBinding',
                   credentialsId: 'artifactory',
                   passwordVariable: 'ARTIFACTORY_PASSWORD',
                   usernameVariable: 'ARTIFACTORY_LOGIN']
          ]) {
              sh '''
                cp ${WORKSPACE}/artifactory/repositories.yaml ${WORKSPACE}/jimbo/conf/
                bash -c "source ${VENV_PATH}/bin/activate && jimbo update-json \
                --url ${ARTIFACTORY_URL} --username ${ARTIFACTORY_LOGIN} \
                --password ${ARTIFACTORY_PASSWORD} && jimbo run --url ${ARTIFACTORY_URL} \
                --username ${ARTIFACTORY_LOGIN} --password ${ARTIFACTORY_PASSWORD} \
                --config-file ${WORKSPACE}/jimbo/conf/repositories.yaml"
              '''
             }
        }
    }
}
