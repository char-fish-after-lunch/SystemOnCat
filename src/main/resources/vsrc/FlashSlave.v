module FlashSlave(dat_i, dat_o, ack_o, adr_i, cyc_i,
    err_o, rty_o, sel_i, stb_i, we_i, stall_o,
    clk_bus, rst_bus,
    flash_a, flash_d, flash_rp_n, flash_vpen,
    flash_ce_n, flash_we_n, flash_oe_n, flash_byte_n, flash_clk);

input wire clk_bus;
input wire rst_bus;

// ----------- system bus slave interface ---------


input wire [31:0] dat_i;
output reg [31:0] dat_o;
output wire ack_o;
input wire [31:0] adr_i;
input wire cyc_i;
output wire err_o;
output wire rty_o;
input wire [3:0] sel_i;
input wire stb_i;
input wire we_i;
output wire stall_o;

// ------------------ flash io ---------------

input wire flash_clk;

output reg [22:0]flash_a; // address
inout  wire [15:0]flash_d; // data
output wire flash_rp_n; // reset
output wire flash_vpen; // protection enabled
output wire flash_ce_n; // chip enabled
output wire flash_oe_n; // output enabled
output wire flash_we_n; // write enabled
output wire flash_byte_n; // 0 = 8x mode, 1 = 16x mode

// sliding window
reg [1:0] read_phase;
reg [1:0] o_read_phase;
reg req, we;
reg [31:0] adr, dat;
reg [7:0] dat_read;

initial begin
    read_phase <= 0;
    o_read_phase <= 0;

    req <= 0;
    adr <= 0;
    we <= 0;
    dat <= 0;
end

always @(posedge clk_bus) begin
    req <= cyc_i & stb_i;
    adr <= adr_i;
    we <= we_i;
    dat <= dat_i;
    if(req && adr[3:2] == 2'b01 && we) begin
        flash_a <= dat[22:0];
        o_read_phase <= read_phase + 2'b10;
    end
end

always @* begin
    case(adr[3:2])
        2'b00: begin
            dat_o = {{24{1'b0}}, dat_read};
        end
        2'b01: begin
            // write address
            dat_o = {32{1'b0}};
        end
        default: begin
            dat_o = {{31{1'b0}}, o_read_phase == read_phase};
        end
    endcase
end



always @(posedge flash_clk) begin
    if(o_read_phase != read_phase) begin
        read_phase <= read_phase + 2'b01;
        if(o_read_phase - read_phase == 1'b01) begin
            dat_read <= flash_d[7:0];
        end
    end
end

assign flash_byte_n = 1'b0;
assign flash_d = {16{1'bZ}}; // only receive data from device
assign flash_vpen = 1'b0;
assign flash_rp_n = 1'b1;

assign ack_o = req;
assign err_o = 0;
assign rty_o = 0;
assign stall_o = 0;

assign flash_ce_n = (o_read_phase - read_phase != 2'b01);
assign flash_we_n = 1'b1;
assign flash_oe_n = 1'b0;

endmodule