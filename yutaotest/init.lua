require("lfs")
function attrdir(path)
    for file in lfs.dir(path) do
        if file ~= "." and file ~= ".." then
            local f = path .. "/" .. file
            local attr = lfs.attributes(f)
            print (f)
            for name, value in pairs(attr) do
                print (name, value)
            end
         end
     end
end