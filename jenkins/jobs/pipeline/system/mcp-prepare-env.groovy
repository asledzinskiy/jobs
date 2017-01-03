def NODE="${env.NODE}"

node(NODE) {

  def DELETE_DB = "${env.DELETE_DB}"
  def VIRTUAL_ENV = "/home/jenkins/venv-fuel-devops-3.0"
  def LOG_FILE = "/home/jenkins/.devops/log.yaml"
  def DEVOPS_DB_ENGINE = "django.db.backends.sqlite3"
  def DEVOPS_DB_NAME = "/home/jenkins/venv-fuel-devops-3.0.sqlite3.db"
  def VENV_REQUIREMENTS = "${env.VENV_REQUIREMENTS}"
  def TESTS_REPO = "${env.TESTS_REPO}"
  def BRANCH = "${env.BRANCH}"
  def WORKSPACE = "${env.WORKSPACE}"

  deleteDir()

  echo "Deleting old VENV stored under " + VIRTUAL_ENV
  dir(VIRTUAL_ENV) {
    deleteDir()
  }
  /*
     fuel-devops use .devops directory to store log configuration
     we need to delete log.yaml before update to get it in current
     version
  */
  withEnv(["VIRTUAL_ENV=${VIRTUAL_ENV}",
           "DEVOPS_DB_ENGINE=${DEVOPS_DB_ENGINE}",
           "DEVOPS_DB_NAME=${DEVOPS_DB_NAME}",
           "BRANCH=${BRANCH}",
           "LOG_FILE=${LOG_FILE}",
           "WORKSPACE=${WORKSPACE}"
          ]) {

    if (fileExists(LOG_FILE)) {
      echo "Deleting devops log file"
      sh "rm ${LOG_FILE}"
    }

    if (DELETE_DB) {
      if (fileExists(DEVOPS_DB_NAME)) {
        sh "rm ${DEVOPS_DB_NAME}"
      }
    }

    stage('Code checkout'){
      sh "git clone ${TESTS_REPO} ."
      sh "git checkout ${BRANCH}"
    }

    stage('Create VENV') {
      sh "virtualenv --no-site-packages ${VIRTUAL_ENV}"
    }

    stage('Prepare the requirements file')
    {
      if(VENV_REQUIREMENTS) {
          echo "Install with custom requirements"
          writeFile file: WORKSPACE + "/venv-requirements.txt", text: VENV_REQUIREMENTS
      } else {
          echo "Using default requirements.txt"
          sh "cp ${WORKSPACE}/fuel_ccp_tests/requirements.txt ${WORKSPACE}/venv-requirements.txt"
      }
    }

    stage('Install the fuel-devops in VENV') {
      writeFile file: WORKSPACE + "/install_devops.sh", text: '''\
        #!/bin/bash -ex
        source ${VIRTUAL_ENV}/bin/activate
        pip install pip --upgrade
        pip install -r "${WORKSPACE}/venv-requirements.txt" --upgrade
        echo "=============================="
        pip freeze
        echo "=============================="
        django-admin.py syncdb --settings=devops.settings --noinput
        django-admin.py migrate devops --settings=devops.settings --noinput
        deactivate
      '''.stripIndent()
      sh "chmod 755 ${WORKSPACE}/install_devops.sh"
      sh(returnStdout: true, script: WORKSPACE + "/install_devops.sh").trim()
    }

    currentBuild.description = NODE
  }

}
