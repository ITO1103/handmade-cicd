pipeline {
    agent any

    // ビルドの同時実行を禁止する (今回は同時に複数ビルドを行わないため発生しないが，ローカルリポジトリを複数ビルドが同時に触ると競合する可能性があるためそれを禁止する)
    options {
        disableConcurrentBuilds()
    }

    stages {
        stage('Prepare Builder') { // C++ビルド用のDockerイメージを作成
            steps {
                sh 'docker build -t cpp-builder:test0 /workspace/builder/cpp'
            }
        }

        stage('Build') { // C++をビルド用コンテナでビルド
            steps {
                sh '''
                    docker run --rm\
                        -v "$HOST_WORKSPACE:/workspace" \
                        -w /workspace \
                        cpp-builder:test0 \
                        sh -lc 'mkdir -p build && g++ -std=c++20 -Wall -Wextra -pedantic src/hello.cpp -o build/hello'
                '''
            }
        }
        stage('Execute') { // ビルドしたC++を実行
            steps {
                timeout(time: 30, unit: 'SECONDS') { // 念の為タイムアウト (対話のやつを入れてしまった場合，無限に入力待ちで終わらない可能性があるので)
                    sh '''
                        docker run --rm \
                            -v "$HOST_WORKSPACE:/workspace" \
                            -w /workspace \
                            cpp-builder:test0 \
                            sh -lc './build/hello'
                    '''
                }
            }
        }
        stage('Output Test') { // ビルドしたC++の出力をテスト (出力に[TEST]が含まれていることを確認する簡単なテスト)
            steps {
                script {
                    def output = sh(script: '''
                        docker run --rm \
                            -v "$HOST_WORKSPACE:/workspace" \
                            -w /workspace \
                            cpp-builder:test0 \
                            sh -lc './build/hello'
                    ''', returnStdout: true).trim()
                    if (!output.contains('TEST')) {
                        error "Unexpected output: ${output}"
                    }
                }
            }
        }

    }

    post {
        always { // ビルド後にクリーンアップ
            sh 'docker run --rm -v "$HOST_WORKSPACE:/workspace" -w /workspace cpp-builder:test0 rm -rf build'
        }
    }
}
