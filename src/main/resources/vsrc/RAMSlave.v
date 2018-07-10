module RAMSlave(dat_i, dat_o, ack_o, adr_i, cyc_i,
    err_o, rty_o, sel_i, stb_i, we_i,
    sram_adr, sram_dat, sram_ce, sram_oe,
    sram_we, sram_be, clk_bus, rst_bus);

input wire clk_bus;
input wire rst_bus;

// ----------- system bus slave interface ---------


input wire [31:0] dat_i;
output wire [31:0] dat_o;
output reg ack_o;
input wire [31:0] adr_i;
input wire cyc_i;
output wire err_o;
output wire rty_o;
input wire [3:0] sel_i;
input wire stb_i;
input wire we_i;

// ------------ SRAM interface --------------
output wire [19:0] sram_adr;
inout wire [31:0] sram_dat;
output wire sram_ce;
output wire sram_oe;
output wire sram_we;
output wire [3:0] sram_be;

reg [31:0] stored_dat;
reg [19:0] target_adr;


localparam STATE_IDLE = 3'b000,
    STATE_READ = 3'b001,
    STATE_WRITE = 3'b010;

reg [2:0] state;

initial begin
    state <= STATE_IDLE;
end

always@(posedge clk_bus) begin
    case(state)
        STATE_IDLE: begin
            ack_o <= 1'b0;
            if (cyc_i && stb_i) begin
                // transaction cycle requested
                target_adr <= adr_i[19:0];
                if(we_i) begin
                    stored_dat <= dat_i;
                    state <= STATE_WRITE;
                end
                else
                    state <= STATE_READ;
            end
        end
        STATE_WRITE: begin
            ack_o <= 1'b1;
            state <= STATE_IDLE;
        end
        STATE_READ: begin
            stored_dat <= sram_dat;
            ack_o <= 1'b1;
            state <= STATE_IDLE;
        end
    endcase
end

//always@* begin
//    case(state)
//        STATE_IDLE: begin
//            sram_ce = 1'b1;
//        end
//        STATE_READ: begin
//            sram_we = 1'b1;
//            sram_dat = {32{1'Z}};
//            sram_ce = 1'b0;
//        end
//        STATE_WRITE: begin
//            sram_we = 1'b0;
//            sram_dat = stored_dat;
//            sra_ce = 1'b0;
//        end
//    endcase
//end
    assign sram_we = state != STATE_WRITE;
    assign sram_ce = state == STATE_IDLE;
    assign sram_dat = state == STATE_WRITE ? stored_dat : {32{1'bZ}};
    
    assign sram_be = 4'b0000;
    assign sram_oe = 1'b0;

    assign dat_o = stored_dat;

    assign err_o = 1'b0;
    assign rty_o = 1'b0;

    assign sram_adr = target_adr;

endmodule
