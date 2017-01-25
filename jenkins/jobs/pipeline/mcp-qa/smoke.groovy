git = new com.mirantis.mcp.Git()
common = new com.mirantis.mcp_qa.Common()
mcp_common = new com.mirantis.mcp.Common()
testRunner = new com.mirantis.mcp_qa.RunTest()
environment = new com.mirantis.mcp_qa.EnvActions()

node('system-test') {
    def gerritHost = env.GERRIT_HOST
    def jobTimeout = 480
    def timestamp = mcp_common.getDatetime()
    def workspace = "${env.WORKSPACE}"
    def projectRepoDir = "${workspace}/project-config-${timestamp}"
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
                sh "/bin/bash ${projectRepoDir}/jenkins/jobs/builders/get-systest-image.sh"
            }
            def additionalParameters = []
            def jobSetParameters = []

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
                    environment.prepareEnv()
                    def deployScript = "${env.WORKSPACE}/fuel-ccp-installer/${env.DEPLOY_SCRIPT_REL_PATH}"
                    jobSetParameters.add("DEPLOY_SCRIPT=${deployScript}")
                }

                stage('set version of downstream k8s artifacts') {
                    def k8sTag = common.getLatestArtifacts('HYPERKUBE_IMAGE_REPO', 'HYPERKUBE_IMAGE_TAG')
                    if (k8sTag) {
                        jobSetParameters.add(k8sTag)
                    }
                }

                stage('set version of downstream calico artifacts') {
                    if (env.CALICO_VERSION != null) {
                        if ( !env.CALICOCTL_IMAGE_TAG ) {
                            env.CALICOCTL_IMAGE_TAG = env.CALICO_VERSION
                        }
                        if ( !env.CALICO_NODE_IMAGE_TAG ) {
                            env.CALICO_NODE_IMAGE_TAG = env.CALICO_VERSION
                        }
                    }
                    def calicoCTL = common.getLatestArtifacts('CALICOCTL_IMAGE_REPO', 'CALICOCTL_IMAGE_TAG')
                    if (calicoCTL) {
                        jobSetParameters.add(calicoCTL)
                    }
                    def calicoNode = common.getLatestArtifacts('CALICO_NODE_IMAGE_REPO', 'CALICO_NODE_IMAGE_TAG')
                    if (calicoNode) {
                        jobSetParameters.add(calicoNode)
                    }
                    def calicoCNI = common.getLatestArtifacts('CALICO_CNI_IMAGE_REPO', 'CALICO_CNI_IMAGE_TAG')
                    if (calicoCNI) {
                        jobSetParameters.add(calicoCNI)
                    }
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

                if (!((env.KEEP_AFTER == "yes") || (env.KEEP_AFTER == "true"))){
                    environment.eraseEnv()
                }
            }
        }
    }
    catch (Exception x) {
        echo x.getMessage()
    }
    finally {
        environment.destroyEnv()
        sh "rm -rf ${projectRepoDir}"
        archiveArtifacts allowEmptyArchive: true, artifacts: '**/nosetests.xml,logs/*,tests.log,*.txt', excludes: null
        junit keepLongStdio: false, testResults: '**/nosetests.xml'
    }
}
