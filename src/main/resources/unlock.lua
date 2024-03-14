--keys[1]是锁的key
--ARGV[1]是线程前缀加上线程id，也是锁的值
if (redis.call("get",KEYS[1])==ARGV[1]) then
   return  redis.call("del",KEYS[1])
end
return 0