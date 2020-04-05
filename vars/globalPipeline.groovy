def call(Map pipelineParameters) {

    pipeline {
        agent any

        tools {
            maven 'MAVEN-3.6.3'
        }

        options {
            disableConcurrentBuilds()
        }

        triggers {
            pollSCM('H/2 * * * *')
        }

        stages {

            stage('Get params') {
                steps {
                    script {
                        if (pipelineParameters != null) {
                            REPO = pipelineParameters.scmUrl
                            BRANCH = pipelineParameters.branch
                            PROJECT_DIR = pipelineParameters.dir
                        }
                    }
                }
            }

            stage('User or SCM?') {
                steps {
                    script {
                        isUserChange = currentBuild.rawBuild.getCauses()[0].toString().contains('UserIdCause')
                    }
                }
            }

            stage('Get user') {
                when {
                    expression {
                        return (isUserChange == true)
                    }
                }
                steps {
                    script {
                        def INPUT_PARAMS = input message: "Who are you?", ok: "Lets go!", parameters: [string(name: 'NAME', description: "Tell me your name...")]
                    }
                }
            }

            stage('Check for readiness') {
                when {
                    expression {
                        return (isUserChange == true)
                    }
                }
                steps {
                    script {
                        def INPUT_PARAMS = input message: "Pulling branch ${BRANCH} from repository ${REPO}", ok: "Start the build now!"
                    }
                }
            }

            stage('Pull Repo') {
                steps {
                    checkout scm: [$class           : 'GitSCM', branches: [[name: "${BRANCH}"]],
                                   userRemoteConfigs: [[credentialsId: 'GrantL', url: "${REPO}"]]]
                }
            }

            stage('Install the application') {
                steps {
                    bat "mvn clean install"
                }
            }

            stage('Save test results') {
                steps {
                    junit 'target/surefire-reports/*.xml'
                }
            }

            stage('Build docker image') {
                steps {
                    bat "docker build ."
                }
            }

//            stage('Clean workspace') {
//                steps {
//                    cleanWs()
//                }
//            }
        }

        post {
            always {
                echo 'Pipeline finished'
            }
            success {
                echo 'Build Successful'
            }
            failure {
                echo 'Build failed'
            }
        }
    }
}