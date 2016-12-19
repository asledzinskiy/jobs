def artifactory = new com.mirantis.mcp.MCPArtifactory()

ARTIFACTORY_SERVER = Artifactory.server("mcp-ci")
ARTIFACTORY_URL = ARTIFACTORY_SERVER.getUrl()
IMAGE_NAMESPACE = "mirantis/ceph"
DEV_REPOSITORY = "docker-dev-local"
PROD_REPOSITORY = "docker-prod-local"


if (!env.DOCKER_REGISTRY) {
    error("DOCKER_REGISTRY must be set")
}

node("decapod") {
    stage("Promote artifacts") {
        def images = ["base", "base-plugins", "api", "controller", "cron",
                      "db-data", "db", "frontend", "migrations"];
        for (image in images) {
            def imageRegistryName = "${IMAGE_NAMESPACE}/decapod/${image}"
            def properties = [
                'com.mirantis.gerritChangeId': "${env.GERRIT_CHANGE_ID}",
                'com.mirantis.targetImg'     : imageRegistryName
            ]
            // Search for an artifact with required properties
            def artifact_uri = artifactory.uriByProperties(ARTIFACTORY_URL, properties)
            if ( artifact_uri ) {
                def buildInfo = artifactory.getPropertiesForArtifact(artifact_uri)
                // promote docker image
                artifactory_tools.promoteDockerArtifact(
                    ARTIFACTORY_URL,
                    DEV_REPOSITORY,
                    PROD_REPOSITORY,
                    imageRegistryName,
                    buildInfo.get('com.mirantis.targetTag').join(','),
                    buildInfo.get('com.mirantis.targetTag').join(',').split('-')[0])
            } else {
                error 'Artifacts were not found, nothing to promote'
            }
        }
    }
}
