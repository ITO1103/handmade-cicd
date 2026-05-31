pipeline {
    agent any

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

    }

    post {
        always { // ビルド後にクリーンアップ
            sh 'docker run --rm -v "$HOST_WORKSPACE:/workspace" -w /workspace cpp-builder:test0 rm -rf build'
        }
    }
}
