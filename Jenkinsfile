properties([[$class: 'jenkins.model.BuildDiscarderProperty', strategy:
            [$class: 'LogRotator', numToKeepStr: '100', artifactNumToKeepStr: '20']
            ]])

pipeline {
    agent any

    environment {
        LD_LIBRARY_PATH = '/usr/lib/jvm/java-11-openjdk-amd64/lib/server:/usr/local/lib'
        JAVA_HOME = '/usr/lib/jvm/java-11-openjdk-amd64'
        PATH = '/home/aion/.cargo/bin:/home/aion/bin:/home/aion/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/snap/bin:/usr/lib/jvm/java-11-openjdk-amd64/bin'
        LIBRARY_PATH = '/usr/lib/jvm/java-11-openjdk-amd64/lib/server'
    }

    stages {
        stage('Build Kernel Java') {
            steps {
                dir('javaKernel') {
                    checkout scm: [$class: 'GitSCM', userRemoteConfigs: [[url: 'https://github.com/aionnetwork/aion.git']], branches: [[name: '125ccb59']]], poll: false
                    sh "./gradlew pack"
                }
                sh('cp javaKernel/pack/oan.tar.bz2 Tests')
                sh('tar -C Tests -xjf Tests/oan.tar.bz2')
            }
        }
        stage('Concurrent Suite Java') {
            steps {
                timeout(20) {
                    sh('./gradlew :Tests:test -i -PtestNodes=java')
                }
            }
        }
        
        stage('Build Kernel Rust') {
            steps {
                dir('rustKernel') {
                    checkout scm: [$class: 'GitSCM', userRemoteConfigs: [[url: 'https://github.com/aionnetwork/aionr.git']], branches: [[name: '6e864a3']]], poll: false
                    sh "./resources/package.sh aionr"
                }
                sh('cp rustKernel/aionr.tar.gz Tests')
                sh('tar -C Tests -xvf Tests/aionr.tar.gz')
            }
        }
        stage('Concurrent Suite Rust') {
            steps {
                timeout(20) {
                    sh('./gradlew :Tests:test -i -PtestNodes=rust')
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }

        success {
            slackSend channel: '#ci',
                color: 'good',
                message: "The pipeline ${currentBuild.fullDisplayName} completed successfully."
        }

        failure {
            slackSend channel: '#ci',
                    color: 'danger',
                    message: "The pipeline ${currentBuild.fullDisplayName} failed at ${env.BUILD_URL}"
        }
    }
}
