---function 函数封装 get_from_cache(key) 
---
---
function get_from_cache(key)
    -- 声明local变量 cache_ngx,变量的值取于nginx的shell
    local cache_ngx = ngx.shared.my_cache
    -- 声明local变量 value ，值为上面定义的 cache_ngx通过get方法获取的.lua调用方用是：号，而不是。号
    local value = cache_ngx:get(key)
    return value
end
-- set进去的 key,value 以及超时时间exptime
function set_to_cache(key,value,exptime)

    -- 如果exptime未定义时
    if not exptime then
            exptime = 0
    end
    -- 取到 cache_ngx
    local cache_ngx = ngx.shared.my_cache
    -- 返回值是一个多重返回值的元组，不只是和Java一样单个返回值
    local succ,err,forcible = cache_ngx:set(key,value,exptime)
    -- 返回succ
    return succ

end

-- 封装 main 方法.通过这个方法拿到Nginx的get请求的uri上的参数，例如id=41
local args =ngx.req.get_uri_args()
-- 通过数组取到id
local id = args["id"]
-- 拼接id，.. 代表拼接，相当于Java中 + 把item_id作为key传入，拿到对应的value
local item_model = get_from_cache("item_"..id)
-- 如果item_model值为null时，即nginx的缓存取不到
if item_model == nil then
    --缓存中取不到就转发到后端的服务器上，通过转拿到结果。转发的url为 "/item/get?id="..id
        local resp =ngx.location.capture("/item/get?id="..id)
    -- 将刚才转发拿到的结果set进item_model这个变量中，然后将缓存起来
        item_model = resp.body
    -- item_model为缓存的value，key为id，缓存时为60秒
        set_to_cache("item_"..id,item_model,1*60)

end
-- 如果item_model值不为null时，即nginx处有缓存。直接通过say方法输出
ngx.say(item_model)