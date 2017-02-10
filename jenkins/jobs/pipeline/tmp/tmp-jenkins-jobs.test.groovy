//
// Function used to analyse diff file output
// return array with differences
//
@NonCPS
def diff_check(diff_data) {

    def output = [
        new: [],
        old: [],
        diff: []
    ]
    diff_data.eachLine { line ->
        def job_file = ''
        def job_type = ''
        def job_name = ''
        if ( line =~ /Files/ ) {
            job_file = line.tokenize()[1]
            job_name = job_file.tokenize('/').last()
            job_type = 'diff'
        } else if ( line =~ /Only in/ ) {
            s1 = line.tokenize(':')
            job_name = s1[1].trim()
            job_type = s1[0].tokenize('/').last()
        }

        if (job_name != '') {
            output[job_type].push(job_name)
        }
    }
    return output
}

def gitTools = new com.mirantis.mcp.Git()

node('tools') {

    env.OUT_DIR = "${env.WORKSPACE}/patch/output/jobs"
    env.LOGFILE = "${env.OUT_DIR}/jobs-diff.log"

    def diff_list = [:]

    dir('patch') {
        stage('patch checkout') {
            gitTools.gerritPatchsetCheckout([
                    credentialsId: "mcp-ci-gerrit"
            ])
        }

        def others = []
        stage('Checking out jobs of other repositories') {
            switch (GERRIT_PROJECT) {
                case "mcp-ci/project-config-base":
                    others = ["project-config-local", "project-config"]
                    break
                case "mcp-ci/project-config-local":
                    others = ["project-config-base", "project-config"]
                    break
                case "mcp-ci/project-config":
                    others = ["project-config-base", "project-config-local"]
                    break
            }
            others.each {
                try {
                    gitTools.gitSSHCheckout([
                            credentialsId: "mcp-ci-gerrit",
                            branch       : "master",
                            host         : GERRIT_HOST,
                            project      : "mcp-ci/${it}",
                            targetDir    : "${WORKSPACE}/others/${it}"
                    ])
                    sh "cp -r ${WORKSPACE}/others/${it}/jenkins/jobs ${WORKSPACE}/patch/jenkins/jobs/${it}"
                }
                catch (ignored) {
                    echo "Repo mcp-ci/${it} is not present"
                }

            }

        }

        stage('JJB verify') {
            sh 'tox -e jjb-generate -- new'
        }

        stage('JJB compare') {
            sh 'git checkout "${BASE_COMMIT}"'
            sh 'tox -e jjb-generate -- old'

            sh 'mkdir -p "${OUT_DIR}/diff"'

            // get return code from diff, when 1 it mean there was differences
            def diff_status = sh returnStatus: true, script: 'diff -q -r ${OUT_DIR}/old ${OUT_DIR}/new >"${LOGFILE}"'
            if (diff_status == 1) {

                // Analyse output file and prepare array with results
                diff_list = diff_check(readFile("${LOGFILE}"))

                // Set job description
                description = ''
                if (diff_list['diff'].size() > 0) {
                    description += '<b>CHANGED</b><ul>'
                    for (item in diff_list['diff']) {
                        description += "<li><a href=\"${env.BUILD_URL}artifact/output/jobs/diff/${item}/*view*/\">${item}</a></li>"
                        // Generate diff file
                        def diff_exit_code = sh returnStatus: true, script: "diff -U 50 ${env.OUT_DIR}/old/${item} ${env.OUT_DIR}/new/${item} > ${env.OUT_DIR}/diff/${item}"
                        // catch normal errors, diff should always return 1
                        if (diff_exit_code != 1) {
                            throw new RuntimeException('Error with diff file generation')
                        }
                    }
                }
                if (diff_list['new'].size() > 0) {
                    description += '<b>ADDED</b><ul>'
                    for (item in diff_list['new']) {
                        description += "<li><a href=\"${env.BUILD_URL}artifact/output/jobs/new/${item}/*view*/\">${item}</a></li>"
                    }
                }
                if (diff_list['old'].size() > 0) {
                    description += '<b>DELETED</b><ul>'
                    for (item in diff_list['old']) {
                        description += "<li><a href=\"${env.BUILD_URL}artifact/output/jobs/old/${item}/*view*/\">${item}</a></li>"
                    }
                }
                if (description != '') {
                    currentBuild.description = description
                } else {
                    currentBuild.description = 'No job changes'
                }
            }
        }


        stage('Publish artifacts') {
            archiveArtifacts allowEmptyArchive: true,
                    artifacts: 'output/**',
                    excludes: null
        }
    }

}
