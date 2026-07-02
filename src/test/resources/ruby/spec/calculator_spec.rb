require_relative '../lib/calculator'

RSpec.describe Calculator do
  it 'adds two numbers' do
    expect(Calculator.new.add(1, 2)).to eq(3)
  end
end
