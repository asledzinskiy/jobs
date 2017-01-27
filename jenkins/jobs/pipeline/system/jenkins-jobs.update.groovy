def gitTools = new com.mirantis.mcp.Git()

node('tools') {

    stage('Code checkout') {
        def HOST = env.GERRIT_HOST
        gitTools.gitSSHCheckout ([
            credentialsId : "mcp-ci-gerrit",
            branch : "master",
            host : HOST,
            project : "mcp-ci/project-config"
        ])
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
            sh "chmod 0400 ${WORKSPACE}/jenkins_jobs.ini"
            sh 'tox -e mcp-ci -r'
            sh """${WORKSPACE}/.tox/mcp-ci/bin/jenkins-jobs \
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
