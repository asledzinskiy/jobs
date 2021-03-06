def parseGerritURL(String gerritURL, String what) {
    assert(gerritURL != null)

    /*
       Looks like it's a bug in a groovy pipeline
       'ssh://foobar:xxx/'.replaceFirst(/^ssh:/, 'http:') return foobar:xxx
    */
    return sh(
            script: "python -c 'from urlparse import urlparse; print urlparse(\"" + gerritURL + "\")." + what + "'",
            returnStdout: true
    ).trim()
}


node {
    def confGerritHost = parseGerritURL(CONF_GERRIT_URL, 'hostname')
    def confGerritPath = parseGerritURL(CONF_GERRIT_URL, 'path')


    def targetDir = "/tmp/conf-repo-" + env.BUILD_ID
    def changedFile = null
    def buildComponent = null

    stage('Checkout env configuration') {
      def gitTools = new com.mirantis.mcp.Git()
      gitTools.gitSSHCheckout ([
        credentialsId : env.CONF_GERRIT_CREDENTIAL_ID,
        branch : "master",
        host : confGerritHost,
        project : confGerritPath,
        targetDir: targetDir
      ])
      changedFile = sh(
        script: """
cd $targetDir
git show `git rev-parse HEAD` | grep -m 1 -oE '\\/(config|version)s\\.yaml\$'
""",
        returnStdout: true
      ).trim()

      buildComponent = sh(
        script: """
cd $targetDir
git show `git rev-parse HEAD` | grep -E '^\\+.*tag:' || :
""",
        returnStdout: true
      ).trim()

    }

    sh "rm -rf $targetDir"

    echo 'changedFile: ' + changedFile

    stage('Apply changes') {
      if (changedFile == '/versions.yaml') {
        build job: 'demo-build', parameters: [
          [$class: 'StringParameterValue', name: 'CCP_COMPONENT', value: (buildComponent != "") ? "horizon" : "" ],
        ]
      }
      build job: 'demo-deploy'

    }
}
