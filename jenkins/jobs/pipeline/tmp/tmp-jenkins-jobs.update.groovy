def gitTools = new com.mirantis.mcp.Git()

node('tools') {

    stage('Jobs checkout') {
        def HOST = env.GERRIT_HOST
        gitTools.gitSSHCheckout ([
                credentialsId : "mcp-ci-gerrit",
                branch : "master",
                host : HOST,
                project : "mcp-ci/project-config-base",
        ])
        gitTools.gitSSHCheckout ([
                credentialsId : "mcp-ci-gerrit",
                branch : "master",
                host : HOST,
                project : "mcp-ci/project-config-local",
                targetDir : "local"
        ])
        sh "cp -r ${WORKSPACE}/local/jenkins/jobs ${WORKSPACE}/jenkins/jobs/local"
        sh "if [ -e ${WORKSPACE}/local/jenkins/views ]; then cp -r ${WORKSPACE}/local/jenkins/views ${WORKSPACE}/jenkins/views/local; fi"
        try {
            gitTools.gitSSHCheckout([
                    credentialsId: "mcp-ci-gerrit",
                    branch       : "master",
                    host         : HOST,
                    project      : "mcp-ci/project-config",
                    targetDir    : "ci"
            ])
            sh "cp -r ${WORKSPACE}/ci/jenkins/jobs ${WORKSPACE}/jenkins/jobs/ci"
            sh "if [ -e ${WORKSPACE}/ci/jenkins/views ]; then cp -r ${WORKSPACE}/ci/jenkins/views ${WORKSPACE}/jenkins/views/ci; fi"
        }
        catch (ignored) {
            echo "No CI repo is present"
        }
    }


    stage('JJB update') {
        try {
            withCredentials([[
                                     $class: 'UsernamePasswordMultiBinding',
                                     credentialsId: 'jjb-updater',
                                     usernameVariable: 'JJB_USER',
                                     passwordVariable: 'JJB_PASS']]) {
                writeFile file: "${WORKSPACE}/jenkins_jobs.ini", text: """\
                    [jenkins]
                    user=${env.JJB_USER}
                    password=${env.JJB_PASS}
                    url=${env.JENKINS_URL}
                    [job_builder]
                    ignore_cache=True
                    recursive=True
                    """.stripIndent()
            }
            sh 'tox -e mcp-ci -r'
            sh """${WORKSPACE}/.tox/mcp-ci/bin/jenkins-jobs  \
                    --flush-cache \
                    --conf ${WORKSPACE}/jenkins_jobs.ini \
                    update --delete-old jenkins/jobs"""
        } catch(err) {
            echo "Failed: ${err}"
            currentBuild.result = 'FAILURE'
        } finally {
            sh "rm -f ${WORKSPACE}/jenkins_jobs.ini"
        }
    }

}
