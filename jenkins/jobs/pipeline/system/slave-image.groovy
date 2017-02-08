docker_dev_repo = "docker-dev-local"
docker_prod_repo = "docker-prod-local"
namespace = 'mirantis/jenkins-slave-images'

artifactory = new com.mirantis.mcp.MCPArtifactory()
artifactoryServer = Artifactory.server("mcp-ci")
artifactory_url = artifactoryServer.getUrl()
common = new com.mirantis.mcp.Common()
gitTools = new com.mirantis.mcp.Git()
buildInfo = Artifactory.newBuildInfo()
docker_registry = env.DOCKER_REGISTRY
base_slave_image = env.BASE_SLAVE_IMAGE
base_image_version = env.BASE_SLAVE_IMAGE_VERSION

if ( env.GERRIT_EVENT_TYPE ) {
    if ( "${env.GERRIT_EVENT_TYPE}" == "change-merged" ) {
        promote_artifacts()
    } else {
        build_artifacts(false)
    }
} else {
    // Manual job trigger for testing purposes
    build_artifacts(true)
}

def build_artifacts (Boolean manual = false) {
    stage('build') {
        node('docker') {
            def mcp_project = env.MCP_PROJECT
            def gerrit_host = env.GERRIT_HOST
            deleteDir()
            if ( manual ) {
                gitTools.gitSSHCheckout ([
                    credentialsId : "mcp-ci-gerrit",
                    branch : "master",
                    host : "${gerrit_host}",
                    project : "${mcp_project}-ci/${mcp_project}-slave-image"
                ])
            } else {
                gitTools.gerritPatchsetCheckout ([
                    credentialsId : "mcp-ci-gerrit"
                ])
            }
            def imageTag = common.getDatetime()

            if ( manual ) {
                // Build from specified parameters
                def sed_string = "${docker_registry}/${namespace}/${base_slave_image}:${base_image_version}"
                sh "sed -i 's,^FROM.*,FROM ${sed_string},g' Dockerfile"
                docker_image = "${namespace}/${mcp_project}-${base_slave_image}"
            } else {
                // Change only registry to local
                def old_registry = sh(script: "sed -rn 's/^FROM +([^\\/]+)\\/.*\$/\\1/p' Dockerfile",
                        returnStdout: true).trim()
                sh "sed -i 's/${old_registry}/${docker_registry}/g' Dockerfile"
                def base_image = sh(script: "sed -rn 's/^FROM +.+\\/(.+):.*\$/\\1/p' Dockerfile",
                        returnStdout: true).trim()
                docker_image = "${namespace}/${mcp_project}-${base_image}"
            }
            // Remove old one if exists
            sh "docker rmi -f ${docker_image} || true"
            sh "docker build -t ${docker_image}:${imageTag} ."

            // Upload
            sh "docker tag ${docker_image}:${imageTag} ${docker_registry}/${docker_image}:${imageTag}"
            artifactory.uploadImageToArtifactory(artifactoryServer,
                    docker_registry,
                     "${docker_image}",
                     "${imageTag}",
                     docker_dev_repo)
            artifactoryServer.publishBuildInfo(buildInfo)
            if ( ! manual ) {
                // Add custom properties for promotion search
                def custom_properties = ['com.mirantis.dockerImageName': "${docker_image}" ]
                def artifact_url = "${artifactory_url}/api/storage/${docker_dev_repo}/${docker_image}/${imageTag}"
                artifactory.setProperties(artifact_url, custom_properties, true)
            }
            // Cleanup
            sh "docker rmi ${docker_image}:${imageTag} ${docker_registry}/${docker_image}:${imageTag}"
            // set job description
            currentBuild.description = """
              <b>${mcp_project}-slave-image</b>: ${docker_registry}/${docker_image}:${imageTag}<br>
            """
        }
    }
}

def promote_artifacts () {
    stage('promote') {
        node('docker') {
            def properties = ['com.mirantis.gerritChangeId': "${env.GERRIT_CHANGE_ID}",
                'com.mirantis.gerritPatchsetNumber': "${env.GERRIT_PATCHSET_NUMBER}"]

            // Search for an artifact with required properties
            def artifact_url = artifactory.uriByProperties(artifactory_url, properties)

            def img_properties = artifactory.getPropertiesForArtifact(artifact_url)
            if ( artifact_url ) {
                def docker_image = img_properties.get('com.mirantis.dockerImageName').join(',')

                def promotionConfig = [
                    'buildName'  : img_properties.get('com.mirantis.buildName').join(','),
                    'buildNumber': img_properties.get('com.mirantis.buildNumber').join(','),
                    'targetRepo' : docker_prod_repo.toString()]
                artifactoryServer.promote promotionConfig

                // copy to docker_prod_repo with the same tag
                artifactory.promoteDockerArtifact(artifactory_url,
                        docker_dev_repo,
                        docker_prod_repo,
                        "${docker_image}",
                        img_properties.get('com.mirantis.targetTag').join(','),
                        img_properties.get('com.mirantis.targetTag').join(','),
                        true)
                // move to docker_prod_repo with the 'latest' tag
                artifactory.promoteDockerArtifact(artifactory_url,
                        docker_dev_repo,
                        docker_prod_repo,
                        "${docker_image}",
                        img_properties.get('com.mirantis.targetTag').join(','),
                        'latest')
            } else {
                throw new RuntimeException("Artifacts were not found, nothing to promote")
            }
        }
    }
}
