def gitTools = new com.mirantis.mcp.Git()
def ciTools = new com.mirantis.mcp.Common()
def artifactory = new com.mirantis.mcp.MCPArtifactory()

ARTIFACTORY_SERVER = Artifactory.server('mcp-ci')
ARTIFACTORY_URL = ARTIFACTORY_SERVER.getUrl()
ARTIFACTORY_PYPI_URL = "${ARTIFACTORY_URL}/api/pypi/pypi-virtual/simple/"
// Decapod make builds images with tags 'decapod/base' etc.
DECAPOD_NAMESPACE = 'mirantis/ceph/'
DECAPOD_BRANCH = env.DECAPOD_BRANCH
DECAPOD_DOCKER_TAG = env.DECAPOD_DOCKER_TAG
DECAPOD_LOGIN = 'root'
DECAPOD_PASSWORD = 'root'
INSTANCE_COUNT = 5 // this is coded in HOT template


node('decapod') {
    stage('Checkout SCM') {
        def gerritHost = env.GERRIT_HOST
        def decapodBranch = DECAPOD_BRANCH

        gitTools.gitSSHCheckout ([
            credentialsId : 'mcp-ci-gerrit',
            branch : decapodBranch,
            host : gerritHost,
            project : 'ceph/decapod',
        ])
    }

    stage('Create Decapod virtualenv') {
        // Currently Decapod is running using docker-compose
        // Decapod requires config version 2 therefore
        // docker-compose>=1.6 should be used.

        sh '''\
            virtualenv -p python2.7 --clear venv
            . venv/bin/activate
            pip install 'docker-compose==1.9.0' jmespath
        '''.stripIndent()
    }

    try {
        stage('Run Decapod') {
            // Decapod configuration is done using environment file
            // which has settings suitable for development needs. It is
            // possible to override these options.
            //
            // After Decapod is up and running, it cannot do anything
            // because it has no users and roles. To add default roles
            // and (or) migrate database from previous version, users
            // have to run migration. This is done with migrate.sh
            // script. Migration script understands what is applied and
            // what is not.

            withEnv([
                "DECAPOD_HTTP_PORT=${env.DECAPOD_HTTP_PORT}",
                "DECAPOD_HTTPS_PORT=${env.DECAPOD_HTTPS_PORT}",
                "DECAPOD_NAMESPACE=${DECAPOD_NAMESPACE}",
                "DECAPOD_REGISTRY_URL=${env.DOCKER_REGISTRY}/",
                "DECAPOD_SSH_PRIVATE_KEY=${env.DECAPOD_SSH_PRIVATE_KEY}",
                "DECAPOD_VERSION=${env.DECAPOD_DOCKER_TAG}",
            ]) {
                sh '''\
                    . venv/bin/activate
                    docker-compose -f ./docker-compose.yml -p decapod up -d
                    ./scripts/migrate.sh -c decapod_database_1 apply
                '''.stripIndent()
            }
        }

        stage('Generate Decapod cloud-config') {
            // Decapod uses cloud-init based server discovery. To do so,
            // it is required to propagate user-data config to remote host.
            // This config activates rc.local and performs server discovery.
            //
            // Basically, during server discovery "calls home" (in terms of
            // cloud-init) and notifies Decapod about its' existence. After
            // that Decapod comes to host and fetches facts from the host.
            //
            // Host appears in the list ONLY if it is accessible by SSH
            // and facts are collectable.

            sh '''\
                . venv/bin/activate
                pip install -e ./decapodlib -e ./decapodcli
                server_discovery_token="$(grep server_discovery_token config.yaml | cut -f 2 -d '"')"
                public_ip="$(ip r g 8.8.8.8 | head -n 1 | awk '{print $NF}')"

                decapod -u "http://${public_ip}:9999" cloud-config \
                        ${server_discovery_token} \
                        containerization/files/devconfigs/ansible_ssh_keyfile.pub \
                    > ./whale_templates/user-data.txt
            '''.stripIndent()
        }

        withCredentials([
            [$class: 'UsernamePasswordMultiBinding',
             credentialsId: 'mcp-jenkins',
             passwordVariable: 'JENKINS_PASSWORD',
             usernameVariable: 'JENKINS_LOGIN']
        ]) {
            withEnv([
                "OS_TENANT_NAME=${env.OS_TENANT_NAME}",
                "OS_AUTH_URL=${env.OS_AUTH_URL}",
                "OS_USERNAME=${JENKINS_LOGIN}",
                "OS_PASSWORD=${JENKINS_PASSWORD}",
                "BUILD_TAG=${env.BUILD_TAG}",
                "DECAPOD_LOGIN=${DECAPOD_LOGIN}",
                "DECAPOD_PASSWORD=${DECAPOD_PASSWORD}",
                "DECAPOD_URL=http://127.0.0.1:${env.DECAPOD_HTTP_PORT}",
                "INSTANCE_COUNT=${INSTANCE_COUNT}"
            ]) {
                try {
                    // Current CI supports OpenStack and we are going to use
                    // OS hosts to deploy Ceph into. This installation is
                    // done using HOT templates for Heat.

                    stage('Boot VMs') {
                        sh '''\
                            . venv/bin/activate

                            pip install python-heatclient
                            cd ./decapod/whale_templates
                            heat stack-create --poll -e parameters.yaml -f heat_template.yaml "$BUILD_TAG"
                        '''.stripIndent()
                    }

                    stage('Create cluster') {
                        // To deploy Ceph, first we have to create cluster
                        // model. This cluster model will be responsible to
                        // hold all data, related to the actual cluster.
                        //
                        // ID of cluster will be stored in cluster_id.txt file

                        sh '''\
                            . venv/bin/activate

                            decapod cluster create ceph | jp.py id | cut -f 2 -d '"' | tee cluster_id.txt
                        '''.stripIndent()
                    }

                    stage('Wait until all servers are discovered') {
                        // It takes time to discover all servers. Because they
                        // have to have deployed OS and executed cloud-init.

                        sh '''\
                            . venv/bin/activate

                            decapod server wait-until 5
                            decapod server get-all -c | tail -n +2 | cut -f 2 -d '"' | tee server_ids.txt
                        '''.stripIndent()
                    }

                    stage('Create playbook configuration') {
                        // To deploy cluster, we need to configure playbook
                        // plugin. Configuration is trivial: we need to
                        // propagate cluster_id and server list to Decapod.

                        sh '''\
                            . venv/bin/activate

                            cat server_ids.txt | \
                            xargs -r decapod playbook-configuration create \
                                deploy \
                                cluster_deploy \
                                $(cat cluster_id.txt) | \
                            jp.py id | cut -f 2 -d '"' | tee playbook_congiuration.txt
                        '''.stripIndent()
                    }

                    stage('Deploy Ceph') {
                        // To deploy Ceph with Decapod, we need to have ID
                        // of playbook configuration and it's version. Default
                        // version is 1. Since, we've just created
                        // configuration, it's version is 1.

                        sh '''\
                            . venv/bin/activate

                            decapod execution create \
                                --wait -1 \
                                $(cat playbook_congiuration.txt) 1
                        '''.stripIndent()
                    }

                    stage('Run Cinder integration') {
                        // To integrate OpenStack with deployed Ceph cluster,
                        // we need to run another playbook.

                        sh '''\
                            . venv/bin/activate

                            decapod playbook-configuration create \
                                cinder \
                                cinder_integration \
                                $(cat cluster_id.txt) | \
                            jp.py id | cut -f 2 -d '"' | tee cinder.txt

                            decapod execution create \
                                --wait -1 \
                                $(cat cinder.txt) 1
                        '''.stripIndent()
                    }

                    stage('Get data for Cinder integration.') {
                        // Request data for Cinder integration.
                        //
                        // Data will be printed in output and stored on
                        // filesystem.

                        sh '''\
                            . venv/bin/activate

                            decapod cluster cinder-integration \
                                --root $(pwd)/cinder \
                                --store \
                                $(cat cluster_id.txt)

                            find ./cinder -type f | \
                            xargs -r -n 1 -I file \
                                sh -c 'echo "\n=================\nfile\n=================\n" && cat file && echo'
                        '''.stripIndent()
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
        stage('Stop Decapod') {
            sh '''\
                . venv/bin/activate
                docker-compose -f ./docker-compose.yml -p decapod down -v
            '''.stripIndent()
        }
    }
}
