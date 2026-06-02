#include <iostream>
 
void memoryLeakExample() {
    int* ptr = new int(10); // メモリの確保
    ptr[0] = 20;    // メモリにアクセス
    // delete ptr; // 解放を忘れている
}
 
int main(){
    memoryLeakExample(); // メモリリークが発生する
    // delete ptr; // ここで解放しても意味がない
    return 0;
}