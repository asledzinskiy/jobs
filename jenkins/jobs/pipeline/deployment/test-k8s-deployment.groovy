def server = Artifactory.server("mcp-ci")
def gitTools = new com.mirantis.mcp.Git()
def String CLUSTER = env.CLUSTER_NAME
def Boolean ERASE_ENV = env.ERASE_ENV
def String INV_SOURCE = env.INV_SOURCE
def String UNDERLAY = env.UNDERLAY ?: "fuel-devops"
def String SLAVE_NODE_LABEL = "mcp-ci-k8s-test-deployment"

@NonCPS
def parseJsonText(String jsonText) {
  final slurper = new groovy.json.JsonSlurperClassic()
  return new HashMap<>(slurper.parseText(jsonText))
}

node("${SLAVE_NODE_LABEL}") {

  def WORKSPACE = "${env.WORKSPACE}"
  def String KARGO_REPO = 'kubernetes/kargo'
  def String KARGO_COMMIT = env.KARGO_COMMIT ?: 'master'

  def ENV_NAME = "mcp-test-deploy-k8s-cluster-${CLUSTER}.${env.BUILD_NUMBER}"
  def ansible_inventory = ""
  def NODE_JSON = "{\"nodes\":" +
        "[{\"node1\":\"null\",\"name\":\"node1\",\"ip\":\"10.109.0.100\",\"bind_ip\":\"10.109.0.100\",\"kube_master\":\"True\",\"etcd\":\"True\"}," +
        "{\"node2\":\"null\",\"name\":\"node2\",\"ip\":\"10.109.0.101\",\"bind_ip\":\"10.109.0.101\",\"kube_master\":\"True\",\"etcd\":\"True\"}," +
        "{\"node3\":\"null\",\"name\":\"node3\",\"ip\":\"10.109.0.102\",\"bind_ip\":\"10.109.0.102\",\"kube_master\":\"False\",\"etcd\":\"True\"}]}"
  // validate NODE_JSON if it is in a working JSON forma
  new groovy.json.JsonSlurperClassic().parseText(NODE_JSON)

  deleteDir()

  stage('code checkout') {
    def HOST = env.GERRIT_HOST
    gitTools.gitSSHCheckout ([
      credentialsId : "mcp-ci-gerrit",
      branch : "master",
      host : HOST,
      project : "mcp-ci/project-config"
    ])

    gitTools.gitSSHCheckout ([
        credentialsId : "mcp-ci-gerrit",
        branch : "${KARGO_COMMIT}",
        host : "${GERRIT_HOST}",
        project : "${KARGO_REPO}",
        targetDir : "kargo"
    ])
  }

  try {
    stage("Run ${CLUSTER}-create-${UNDERLAY}-env job") {
        build job: "${CLUSTER}-create-${UNDERLAY}-env",
            parameters: [
                      string(name: 'SLAVE_NODE_LABEL', value: "${SLAVE_NODE_LABEL}"),
                      booleanParam(name: 'TEST_MODE', value: true) ]
    }
    stage("Get node IPs and generate inventory") {
        step([$class: 'CopyArtifact', filter: 'node-ips.txt, erase_env.sh',
          fingerprintArtifacts: true,
          projectName: "${CLUSTER}-create-${UNDERLAY}-env"])
        sh 'echo node ips; cat node-ips.txt'
        def node_ips_string = readFile "${WORKSPACE}/node-ips.txt"

        //TODO(mattymo): Move to pipeline-library
        //approach A: modify json object and still template
        def node_ips = node_ips_string.split()
        def node_json = parseJsonText(NODE_JSON)
        for(int i=0; i<node_json.nodes.size();i++) {
          node_json.nodes[i].ip = node_ips[i]
        }
        NODE_JSON = groovy.json.JsonOutput.toJson(node_json)

        //approach B: use kargo inventorybuilder with node ip list
        withEnv(["CONFIG_FILE=${WORKSPACE}/inventory.cfg"]) {
          sh "python3 ${WORKSPACE}/kargo/contrib/inventory_builder/inventory.py ${node_ips_string}"
          ansible_inventory = readFile "${WORKSPACE}/inventory.cfg"
        }
    }

    stage("Run ${CLUSTER}-configure-system-test job") {
        build job: "${CLUSTER}-configure-system-test",
            parameters: [
                      string(name: 'SLAVE_NODE_LABEL', value: "${SLAVE_NODE_LABEL}"),
                      string(name: 'NODE_JSON', value: "${NODE_JSON}"),
                      textParam(name: 'ANSIBLE_INVENTORY', value: "${ansible_inventory}"),
                      string(name: 'INV_SOURCE', value: "${INV_SOURCE}"),
                      booleanParam(name: 'TEST_MODE', value: true) ]
    }

    stage("Run ${CLUSTER}-deploy-k8s-test job") {
        build job: "${CLUSTER}-deploy-k8s-test",
            parameters: [
                      string(name: 'SLAVE_NODE_LABEL', value: "${SLAVE_NODE_LABEL}"),
                      string(name: 'NODE_JSON', value: "${NODE_JSON}"),
                      textParam(name: 'ANSIBLE_INVENTORY', value: "${ansible_inventory}"),
                      string(name: 'INV_SOURCE', value: "${INV_SOURCE}")]
    }
  } catch (InterruptedException x) {
      echo "The job was aborted"
      echo x.getMessage()
  } catch (err) {
      echo "The job was aborted"
      echo err.getMessage()
  } finally {
      if (ERASE_ENV) {
        sh "${WORKSPACE}/erase_env.sh"
      }
  }
}
