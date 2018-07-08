module RAMSlave(dat_i, dat_o, ack_o, adr_i, adr_i, cyc_i,
    stall_o, err_o, rty_o, sel_i, stb_i, we_i,
    sram_adr, sram_dat, sram_ce, sram_oe,
    sram_we, sram_lb, sram_ub, clock_bus, rst_bus, clk_ram, rst_ram);

input clk_bus;
input rst_bus;

// ----------- system bus slave interface ---------

input clk_ram;
input rst_ram;

input [31:0] dat_i;
output [31:0] dat_o;
output ack_o;
input [31:0] adr_i;
input cyc_i;
output stall_o;
output err_o;
output rty_o;
input [3:0] sel_i;
input stb_i;
input we_i;

// ------------ SRAM interface --------------
output [19:0] sram_adr;
inout [31:0] sram_dat;
output sram_ce;
output sram_oe;
output sram_we;
output sram_lb;
output sram_ub;


localparam STATE_IDLE = 3'b000,
    STATE_READ = 3'b001;
    STATE_WRITE = 3'b010;

wire [2:0] state;

always@(posedge clk_ram) begin
    case(state)
        STATE_IDLE: begin
            if (cyc_i and stb_i) begin
                // transaction cycle requested
                if(we_i)
                    state = STATE_WRITE;
                else
                    state = STATE_READ;
            end
        end
        STATE_WRITE: begin
            
        end
        STATE_READ: begin
        end
    endcase
end

always@* begin
    case(state)
        STATE_IDLE: begin
            sram_ce = 1'b1;
        end
        STATE_READ: begin
            sram_we = 1'b1;
            sram_ce = 1'b0;
        end
        STATE_WRITE: begin
            sram_we = 1'b0;
            sram_ce = 1'b0;
        end
    endcase
end

endmodule
