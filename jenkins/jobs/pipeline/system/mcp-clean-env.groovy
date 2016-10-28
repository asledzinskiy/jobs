def NODE="${env.NODE}"

node(NODE) {

  def ACTION="${env.ACTION}"
  def ENV_NAME="${env.ENV_NAME}"
  def VIRTUAL_ENV = "/home/jenkins/venv-fuel-devops-3.0"
  def DEVOPS_DB_ENGINE = "django.db.backends.sqlite3"
  def DEVOPS_DB_NAME = "/home/jenkins/venv-fuel-devops-3.0.sqlite3.db"

  if (!ACTION) {
    throw new RuntimeException('No action has been specified!')
  }

  if (!ENV_NAME) {
    throw new RuntimeException('No ' + ENV_NAME + ' has been set!')
  }

  stage('Process the environment') {
    if(fileExists(VIRTUAL_ENV)) {
      withEnv(["VIRTUAL_ENV=${VIRTUAL_ENV}",
               "DEVOPS_DB_ENGINE=${DEVOPS_DB_ENGINE}",
               "DEVOPS_DB_NAME=${DEVOPS_DB_NAME}"]) {
        sh '''
          bash -c "source ${VIRTUAL_ENV}/bin/activate && dos.py ${ACTION} ${ENV_NAME}"
        '''
      }
    } else {
      throw new RuntimeException('Unable to find the ' + VIRTUAL_ENV + ' dir!')
    }
  }
}
