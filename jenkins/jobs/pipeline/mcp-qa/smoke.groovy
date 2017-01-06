git = new com.mirantis.mcp.Git()
common = new com.mirantis.mcp-qa.Common()
testRunner = new com.mirantis.mcp-qa.RunTest()
env = new com.mirantis.mcp-qa.EnvActions()

node('system-test') {
    def gerritHost = env.GERRIT_HOST
    def jobTimeout = 480
    def timestamp = System.currentTimeMillis().toString()
    def projectRepoDir = "/tmp/project-config-${timestamp}"
    try{
        git.gitSSHCheckout ([
          credentialsId : 'mcp-ci-gerrit',
          branch : 'master',
          host : gerritHost,
          project : 'mcp-ci/project-config',
          targetDir : projectRepoDir
        ])
        timeout(jobTimeout) {
            stage('download mcp systest image') {
                sh "${projectRepoDir}/jenkins/jobs/builders/get-systest-image.sh"
            }
            def additionalParameters = []
            def jobSetParameters = []
            def workspace = "${env.WORKSPACE}"

            if (env.ADDITIONAL_PARAMETERS) {
                additionalParameters = "${env.ADDITIONAL_PARAMETERS}".split('\n')
                echo("Additional parameters are: ${additionalParameters.join(' ')}")
            }
            withEnv(additionalParameters) {
                stage('checking disable of net.bridge.bridge-nf-call-iptables') {
                    def res = sh(script: 'cat /proc/sys/net/bridge/bridge-nf-call-iptables',
                                 returnStdout: true).trim()
                    if ( ! res.equals('0') ) {
                         error("Kernel parameter 'net.bridge.bridge-nf-call-iptables' should be disabled to run the tests!")
                    }
                }

                stage('preparing source repos') {
                    git.gitSSHCheckout ([
                      credentialsId : 'mcp-ci-gerrit',
                      branch : env.MCP_QA_COMMIT,
                      host : gerritHost,
                      project : 'mcp/mcp-qa',
                      targetDir : '.'
                    ])
                    git.gitSSHCheckout ([
                      credentialsId : 'mcp-ci-gerrit',
                      branch : env.FUEL_CCP_INSTALLER_COMMIT,
                      host : gerritHost,
                      project : 'ccp/fuel-ccp-installer',
                      targetDir : 'fuel-ccp-installer'
                    ])
                    if ( env.MCP_QA_REFS && ! env.MCP_QA_REFS.equals('none') ) {
                         def refs = "${MCP_QA_REFS}".split("\n")
                         common.getCustomRefs("https://${gerritHost}", 'mcp/mcp-qa', workspace, refs)
                    }
                    if ( env.FUEL_CCP_INSTALLER_REFS && ! env.FUEL_CCP_INSTALLER_REFS.equals('none') ) {
                         def refs = "${FUEL_CCP_INSTALLER_REFS}".split("\n")
                         common.getCustomRefs("https://${gerritHost}", 'ccp/fuel-ccp-installer', workspace, refs)
                    }
                }

                stage('prepare environment') {
                    env.prepareEnv()
                    def deployScript = "${env.WORKSPACE}/fuel-ccp-installer/${env.DEPLOY_SCRIPT_REL_PATH}"
                    jobSetParameters.add("DEPLOY_SCRIPT=${deployScript}")
                }

                stage('prepare underlay') {
                    def group = "-m prepare_underlay"
                    testRunner.runTest(group, jobSetParameters)
                }

                stage('deploy k8s') {
                    def group = "-m prepare_k8s"
                    testRunner.runTest(group, jobSetParameters)
                }

                stage('deploy_ccp') {
                    def group = "-m prepare_ccp"
                    testRunner.runTest(group, jobSetParameters)
                }

                stage('deploy OpenStack') {
                    def group = "-m smoke"
                    testRunner.runTest(group, jobSetParameters)
                }
            }
        }
    }
    catch (Exception x) {
        echo x.getMessage()
    }
    finally {
        env.destroyEnv()
        sh "rm -rf ${projectRepoDir}"
        archiveArtifacts allowEmptyArchive: true, artifacts: '**/nosetests.xml,logs/*,tests.log,*.txt', excludes: null
        junit keepLongStdio: false, testResults: '**/nosetests.xml'
    }
}
