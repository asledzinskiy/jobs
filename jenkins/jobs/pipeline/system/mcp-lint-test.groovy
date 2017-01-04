def gitTools = new com.mirantis.mcp.Git()
node('verify-tests') {

  def RULES_EXCLUDE="ANSIBLE0010,ANSIBLE0012,E511"
  def RULES_PROJECT="https://github.com/tsukinowasha/ansible-lint-rules"

  stage('Code checkout') {
    gitTools.gerritPatchsetCheckout {
      credentialsId = "mcp-ci-gerrit"
      withWipeOut = true
    }
  }

  stage('Run tests') {
    withEnv(["RULES_EXCLUDE=${RULES_EXCLUDE}",
             "RULES_PROJECT=${RULES_PROJECT}", ]) {
      sh '''
        if git diff HEAD~1 --name-only --diff-filter=AM | grep ".*yml$"; then
          tox -e ansible-lint
        else
          echo "No playbooks were changed - skipping the ansible-lint"
        fi
      '''
    }
  }
}
