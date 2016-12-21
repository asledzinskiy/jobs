def gitTools = new com.mirantis.mcp.Git()
def ciTools = new com.mirantis.mcp.Common()
def artifactory = new com.mirantis.mcp.MCPArtifactory()

ARTIFACTORY_SERVER = Artifactory.server("mcp-ci")
ARTIFACTORY_URL = ARTIFACTORY_SERVER.getUrl()
ARTIFACTORY_PYPI_URL = "${ARTIFACTORY_URL}/api/pypi/pypi-virtual/simple/"
// Decapod make builds images with tags 'decapod/base' etc.
DECAPOD_NAMESPACE = "mirantis/ceph/"
WHALE_BRANCH = env.WHALE_BRANCH
DECAPOD_BRANCH = env.DECAPOD_BRANCH
DECAPOD_DOCKER_TAG = env.DECAPOD_DOCKER_TAG


node("decapod") {
    stage("Checkout SCM") {
        def gerritHost = env.GERRIT_HOST
        def whaleBranch = WHALE_BRANCH
        def decapodBranch = DECAPOD_BRANCH

        gitTools.gitSSHCheckout ([
            credentialsId : "mcp-ci-gerrit",
            branch : whaleBranch,
            host : gerritHost,
            project : "ceph/whale"
        ])
        gitTools.gitSSHCheckout ([
            credentialsId : "mcp-ci-gerrit",
            branch : decapodBranch,
            host : gerritHost,
            project : "ceph/decapod",
            targetDir : "./decapod"
        ])
    }

    stage("Create Decapod virtualenv") {
        sh '''\
            python3 -m virtualenv -p python2.7 --clear venv
            . venv/bin/activate
            pip install 'docker-compose==1.9.0'
        '''.stripIndent()
    }

    try {
        stage("Run Decapod") {
            withEnv([
                "DECAPOD_REGISTRY_URL=${env.DOCKER_REGISTRY}",
                "DECAPOD_NAMESPACE=${DECAPOD_NAMESPACE}"
            ]) {
                sh '''\
                    . venv/bin/activate
                    docker-compose -f ./decapod/docker-compose.yml -p decapod up -d
                    ./scripts/migrate.sh -c decapod_database_1
                '''.stripIndent()
            }
        }

        stage("Generate Decapod cloud-config") {
            sh '''\
                . venv/bin/activate
                cd ./decapod
                pip install -e ./buildtools
                pip install -e ./decapodlib -e ./decapodcli
                make copy_example_keys
                chmod 0600 ansible_ssh_keyfile.pem
                ssh-keygen -y -f ansible_ssh_keyfile.pem > public

                server_discovery_token="$(grep server_discovery_token config.yaml | cut -f 2 -d '"')"
                public_ip="$(ip r g 8.8.8.8 | head -n 1 | awk '{print $NF}')"

                decapod -u "http://${public_ip}:9999" cloud-config ${server_discovery_token} ./public > ../user-data.txt
            '''.stripIndent()
        }

        withCredentials([
            [$class: "UsernamePasswordMultiBinding",
             credentialsId: "mcp-jenkins",
             passwordVariable: "JENKINS_PASSWORD",
             usernameVariable: "JENKINS_LOGIN"]
        ]) {
            withEnv([
                "OS_TENANT_NAME=${env.OS_TENANT_NAME}",
                "OS_AUTH_URL=${env.OS_AUTH_URL}",
                "OS_USERNAME=${JENKINS_LOGIN}",
                "OS_PASSWORD=${JENKINS_PASSWORD}",
                "BUILD_TAG=${env.BUILD_TAG}"
            ]) {
                try {
                    stage("Boot VMs") {
                        sh '''\
                            . venv/bin/activate
                            pip install python-heatclient
                            cd ./decapod/whale_templates
                            heat stack-create --poll -e parameters.yaml -f heat_template.yaml "$BUILD_TAG"
                        '''.stripIndent()
                    }

                    stage("Run tests") {
                        // ciTools.runTox "test"
                    }
                } catch(err) {
                    echo 'Build is aborted, start to cleanup Heat stack'
                    currentBuild.result = 'FAILURE'
                } finally {
                    sh '''\
                        . venv/bin/activate
                        heat stack-delete "$BUILD_TAG"
                    '''.stripIndent()
                }
            }
        }
    } catch(err) {
        echo 'Build is aborted, stop Decapod instance.'
        currentBuild.result = 'FAILURE'
    } finally {
        stage("Stop Decapod") {
            sh '''\
                . venv/bin/activate
                docker-compose -f ./decapod/docker-compose.yml -p decapod down -v
            '''.stripIndent()
        }
    }
}
