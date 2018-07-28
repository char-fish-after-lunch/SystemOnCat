mods= basic interrupt mmu atomic

all: $(mods)

define make-submodule-test
$1: ./$1/Makefile
	cd ./$1
	make -C ./$1
	cd ./../
endef

$(foreach mod,$(mods),$(eval $(call make-submodule-test,$(mod))))
