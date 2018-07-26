package systemoncat.mmu

import chisel3._
import chisel3.util._

object MemoryConsts{
	//Memory Address Length
	val VaLength = 32
	val PaLength = 21

	//Virtual Address Structure
	// |----VPN(31:22)----|----VPN(21:12)----|--offset(11:0)--|
	val VPNLength1 = 10
	val VPNLength2 = 10
	val VPNLength = VPNLength1 + VPNLength2
	val OffsetLength = 12

	//Physical Address Structure
	// |----PPN(20)----|--offset(12)--|
	//val PPNLength = 9
	val PPNLength = 20


	//TLB Entry Structure
	// |----ASID(5)----|----VPN(31:16)----|--index(VPN(16:12))--|----PPN(9)----|
	val ASIDLength = 5

	val TLBIndexLength = 5
	val TLBTagLength = VaLength - OffsetLength - TLBIndexLength + ASIDLength // Asid for 5 bit and VPN for 15bit
	val TLBEntryNum = 32

	//Memroy Command
	val Load = 0.U(2.W)
	val Store = 1.U(2.W)
	val PC = 2.U(2.W)
	val Reserverd = 3.U(2.W)

	//PTE Structure
	//|--------PPN(22)--------|---RSW(2)---|D|A|G|U|X|W|R|V|
	//|------PPN(9)------|---RSW(2)---|D|A|G|U|X|W|R|V|
	val RSWLength = 2
	val PTEZero = 32 - PPNLength - RSWLength - 8
}