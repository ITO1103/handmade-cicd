pipeline {
    agent any

    // ビルドの同時実行を禁止する (今回は同時に複数ビルドを行わないため発生しないが，ローカルリポジトリを複数ビルドが同時に触ると競合する可能性があるためそれを禁止する)
    options {
        disableConcurrentBuilds()
    }

    stages {
        stage('Prepare CppCheck') { // C++静的解析(cppcheck)用のDockerイメージを作成
            steps {
                sh 'docker build -t cppcheck:test0 /workspace/cppcheck'
            }
        }

        stage('CppCheck_overflow') { // C++静的解析の実行
            steps {
                sh 'docker run --rm -v "$HOST_WORKSPACE:/workspace" \
                        -w /workspace \
                        cppcheck:test0 \
                        cppcheck --enable=all \
                        src/overflow.cpp'
                    // style/performance/portability/information/unusedFunction/missingIncludeはUNSTABLE
                    // warningはerror
                    // なにも出なければSUCCESSにしたい
            }
        }

        stage('CppCheck_memleak') { // C++静的解析の実行
            steps {
                sh 'docker run --rm -v "$HOST_WORKSPACE:/workspace" \
                        -w /workspace \
                        cppcheck:test0 \
                        cppcheck --enable=all \
                        src/memleak.cpp'
            }
        }

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
                timeout(time: 30, unit: 'SECONDS') { // 念の為タイムアウト (対話のやつを入れてしまった場合，無限に入力待ちで終わらない可能性があるので) // 入力はとりあえず0を入れておく(入れないと入力待ちでタイムアウトになる)
                    sh '''
                        printf '0\n0\n' | docker run --rm -i \
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
                        printf '0\n0\n' | docker run --rm -i \
                            -v "$HOST_WORKSPACE:/workspace" \
                            -w /workspace \
                            cpp-builder:test0 \
                            sh -lc './build/hello'
                    ''', returnStdout: true).trim()
                    echo "Output: ${output}" // 出力を表示させる
                    if (!output.contains('TEST')) {
                        error "Unexpected output: ${output}"
                    }
                }
            }
        }
        stage('Sum TEST') { // ビルドしたC++の入力に対する出力をテスト (入力に3と5を与えたときに出力に[Sum: 8]が含まれていることを確認する)
            steps {
                script { // echoでは入力を渡せなかったのでprintfで入力を渡す
                    def output = sh(script: '''
                        printf '3\n5\n' | docker run --rm -i \
                            -v "$HOST_WORKSPACE:/workspace" \
                            -w /workspace \
                            cpp-builder:test0 \
                            sh -lc './build/hello'
                    ''', returnStdout: true).trim()
                    echo "Output: ${output}" // 出力を表示
                    if (!output.contains('Sum: 8')) {
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
