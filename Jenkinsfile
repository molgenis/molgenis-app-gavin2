pipeline {
    agent {
        kubernetes {
            label 'molgenis-jdk11'
        }
    }
    environment {
        LOCAL_REPOSITORY = "${LOCAL_REGISTRY}/molgenis/molgenis-app-gavin2"
        CHART_VERSION = '0.12.0'
        TIMESTAMP = sh(returnStdout: true, script: "date -u +'%F_%H-%M-%S'").trim()
    }
    stages {
        stage('Retrieve build secrets') {
            steps {
                container('vault') {
                    script {
                        sh "mkdir ${JENKINS_AGENT_WORKDIR}/.m2"
                        sh "mkdir ${JENKINS_AGENT_WORKDIR}/.rancher"
                        sh(script: "vault read -field=value secret/ops/jenkins/rancher/cli2.json > ${JENKINS_AGENT_WORKDIR}/.rancher/cli2.json")
                        sh(script: "vault read -field=value secret/ops/jenkins/maven/settings.xml > ${JENKINS_AGENT_WORKDIR}/.m2/settings.xml")
                        env.SONAR_TOKEN = sh(script: 'vault read -field=value secret/ops/token/sonar', returnStdout: true)
                        env.GITHUB_TOKEN = sh(script: 'vault read -field=value secret/ops/token/github', returnStdout: true)
                        env.PGP_PASSPHRASE = 'literal:' + sh(script: 'vault read -field=passphrase secret/ops/certificate/pgp/molgenis-ci', returnStdout: true)
                        env.CODECOV_TOKEN = sh(script: 'vault read -field=molgenis-app-gavin2 secret/ops/token/codecov', returnStdout: true)
                        env.GITHUB_USER = sh(script: 'vault read -field=username secret/ops/token/github', returnStdout: true)
                    }
                }
                dir("${JENKINS_AGENT_WORKDIR}/.m2") {
                    stash includes: 'settings.xml', name: 'maven-settings'
                }
            }
        }
        stage('Steps [ PR ]') {
            when {
                changeRequest()
            }
            environment {
                // PR-1234-231
                TAG = "PR-${CHANGE_ID}-${BUILD_NUMBER}"
                // 0.0.0-SNAPSHOT-PR-1234-231
                PREVIEW_VERSION = "0.0.0-SNAPSHOT-${TAG}"
            }
            stages {
                stage('Build [ PR ]') {
                    steps {
                        container('maven') {
                            sh "mvn -q -B versions:set -DnewVersion=${PREVIEW_VERSION} -DgenerateBackupPoms=false"
                            sh "mvn clean install -Dmaven.test.redirectTestOutputToFile=true -DskipITs -T4"
                            fetch_codecov()
                            sh "./codecov -c -F unit -K -C ${GIT_COMMIT}"
                            sh "mvn -q -B sonar:sonar -Dsonar.login=${env.SONAR_TOKEN} -Dsonar.github.oauth=${env.GITHUB_TOKEN} -Dsonar.projectName=molgenis-app-gavin2 -Dsonar.pullrequest.base=${CHANGE_TARGET} -Dsonar.pullrequest.branch=${BRANCH_NAME} -Dsonar.pullrequest.key=${env.CHANGE_ID} -Dsonar.pullrequest.provider=GitHub -Dsonar.pullrequest.github.repository=molgenis/molgenis-app-gavin2 -Dsonar.ws.timeout=120"
                        }
                    }
                    post {
                        always {
                            junit '**/target/surefire-reports/**.xml'
                        }
                    }
                }
                stage('Push to registries [ PR ]') {
                    steps {
                        container('maven') {
                            dir('molgenis-app-gavin2') {
                                script {
                                    sh "mvn -q -B dockerfile:build dockerfile:tag dockerfile:push -Ddockerfile.tag=${TAG} -Ddockerfile.repository=${LOCAL_REPOSITORY}"
                                }
                            }
                        }
                    }
                }
            }
        }
        stage('Steps [ master ]') {
            when {
                branch 'master'
            }
            environment {
                TAG = "dev-${TIMESTAMP}"
            }
            stages {
                stage('Build [ master ]') {
                    steps {
                        container('maven') {
                            sh "mvn -q -B clean install -Dmaven.test.redirectTestOutputToFile=true -DskipITs -T4"
                            fetch_codecov()
                            sh "./codecov -c -F unit -K -C ${GIT_COMMIT}"
                            sh "mvn -q -B sonar:sonar -Dsonar.login=${SONAR_TOKEN} -Dsonar.projectName=molgenis-app-gavin2 -Dsonar.ws.timeout=120"
                        }
                    }
                    post {
                        always {
                            junit '**/target/surefire-reports/**.xml'
                        }
                    }
                }
                stage('Push to registries [ master ]') {
                    steps {
                        container('maven') {
                            dir('molgenis-app-gavin2') {
                                script {
                                    sh "mvn -q -B dockerfile:build dockerfile:tag dockerfile:push -Ddockerfile.tag=${TAG} -Ddockerfile.repository=${LOCAL_REPOSITORY}"
                                    sh "mvn -q -B dockerfile:tag dockerfile:push -Ddockerfile.tag=dev -Ddockerfile.repository=${LOCAL_REPOSITORY}"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
