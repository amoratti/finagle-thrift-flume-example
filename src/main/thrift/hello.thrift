namespace java sample.thrift

service Hello {
  string hi();
  
  i32 add(1: i32 a, 2: i32 b);

  i32 blocking_call();
}