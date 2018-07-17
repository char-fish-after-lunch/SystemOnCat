package systemoncat.mmu

import chisel3._
import chisel3.util._

object MemoryConsts{
	//Memory Address Length
	val VaLength = 32
	val PaLength = 21

	//Virtual Address Structure
	// |----VPN(31:22)----|----VPN(21:12)----|--offset(11:0)--|
	val VPNLength = 20
	val PageOffset = 12

	//Physical Address Structure
	// |----PPN(9)----|--offset(12)--|
	val PPNLength = 9

	//TLB Entry Structure
	// |----ASID(5)----|----VPN(31:16)----|--index(VPN(16:12))--|----PPN(9)----|
	val ASIDLength = 5
	val TLBIndexLength = 5
	val TLBTagLength = VaLength - PageOffset - TLBIndexLength + ASIDLength // Asid for 5 bit and VPN for 15bit
	val TLBEntryNum = 32

	//Memroy Command
	val Store = 0.U(2.W)
	val Load = 1.U(2.W)
	val PC = 2.U(2.W)
	val Reserverd = 3.U(2.W)

	//PTE Structure
	//|------PPN(9)------|---RSW(2)---|D|A|G|U|X|W|R|V|
	val RSWLength = 2
	val PTEZero = 32 - PPNLength - RSWLength - 8
}